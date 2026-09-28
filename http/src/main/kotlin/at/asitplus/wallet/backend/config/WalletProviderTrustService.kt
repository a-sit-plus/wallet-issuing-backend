package at.asitplus.wallet.backend.config

import at.asitplus.etsi.TrustListPayload
import at.asitplus.etsi.TrustedEntitiesList
import at.asitplus.etsi.TrustedEntityServices
import at.asitplus.signum.indispensable.josef.JwsCompact
import at.asitplus.wallet.lib.etsi.LoTEFilterService
import at.asitplus.wallet.lib.etsi.LoTEFilterCriteria
import at.asitplus.wallet.lib.etsi.LoTEServiceType
import at.asitplus.wallet.lib.jws.VerifyJwsObjectJades
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.springframework.core.io.ResourceLoader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.cert.X509Certificate
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Resolves the Commission Wallet Provider LoTE and the local A-SIT root for WIA and KA verification. */
class WalletProviderTrustService(
    configuration: WalletAttestationConfiguration,
    resourceLoader: ResourceLoader,
    private val fetch: suspend (String) -> String = ::fetchWalletProviderLote,
    private val clock: Clock = Clock.System,
) {
    private val urls = configuration.walletProviderLoteUrls.distinct().also { urls ->
        require(urls.isNotEmpty()) { "At least one Wallet Provider LoTE URL is required" }
        urls.forEach { url ->
            require(URI(url).scheme.equals("https", ignoreCase = true)) { "Wallet Provider LoTE URL must use HTTPS" }
        }
    }
    private val baseAnchors = loadAttestationAnchors(
        listOf("classpath:certificates/asit-iaca-2026.pem") + configuration.trustedCertificates,
        resourceLoader,
    )
    private val keyAttestationExtras = loadAttestationAnchors(
        configuration.trustedKeyAttestationCertificates, resourceLoader,
    )
    private val loteSignerAnchors = loadAttestationAnchors(configuration.loteSignerCertificates, resourceLoader)
    private val verifier = VerifyJwsObjectJades(
        verifyJwsObject = if (loteSignerAnchors.isEmpty())
            at.asitplus.wallet.lib.jws.VerifyJwsObject()
        else trustedAttestationVerifier { loteSignerAnchors },
    )
    private val refreshMutex = Mutex()
    private data class State(
        val anchors: Set<X509Certificate> = emptySet(),
        val expiry: Instant = Instant.DISTANT_PAST,
        val nextRefresh: Instant = Instant.DISTANT_PAST,
    )
    private val states = urls.associateWith { State() }.toMutableMap()

    suspend fun walletProviderAnchors(): Set<X509Certificate> =
        baseAnchors + currentLoteAnchors()

    suspend fun keyAttestationAnchors(): Set<X509Certificate> =
        walletProviderAnchors() + keyAttestationExtras

    private suspend fun currentLoteAnchors(): Set<X509Certificate> {
        refreshMutex.withLock {
            urls.forEach { url ->
                val now = clock.now()
                val current = states.getValue(url)
                if (now < current.nextRefresh) return@forEach
                states[url] = current.copy(nextRefresh = now + 5.minutes)
                try {
                    val (jws, payload) = JwsCompact.parse<TrustListPayload>(fetch(url)).getOrThrow()
                    verifier(jws).getOrThrow()
                    val lote = payload.loTe
                    val validity = requireNotNull(lote.listAndSchemeInformation) { "Wallet Provider LoTE has no validity" }
                    require(validity.listIssueDateTime <= now && now < validity.nextUpdate) {
                        "Wallet Provider LoTE is not currently valid"
                    }
                    require(validity.loteType?.toString() == WALLET_LOTE_TYPE &&
                        validity.statusDeterminationApproach?.toString() == WALLET_STATUS_APPROACH &&
                        validity.schemeTerritory?.string == "EU" &&
                        validity.schemeTypeCommunityRules?.any {
                            it.uniformResourceIdentifier.toString() == WALLET_COMMUNITY_RULES
                        } == true
                    ) { "Unexpected Wallet Provider LoTE profile" }
                    // VC-K 7.0's generic filter matches the word "wallet" in both issuance and
                    // revocation service types. Scope it to issuance before asking VC-K to extract anchors.
                    val issuanceEntities = lote.trustedEntitiesList.orEmpty().mapNotNull { entity ->
                        val services = entity.trustedEntityServices.filter {
                            it.serviceInformation.serviceTypeIdentifier?.string == WALLET_ISSUANCE_TYPE
                        }
                        if (services.isEmpty()) null
                        else entity.copy(trustedEntityServices = TrustedEntityServices(services))
                    }
                    val scopedLote = lote.copy(
                        trustedEntitiesList = issuanceEntities.takeIf { it.isNotEmpty() }?.let(::TrustedEntitiesList),
                    )
                    val factory = java.security.cert.CertificateFactory.getInstance("X.509")
                    val anchors = LoTEFilterService()
                        .extractTrustedCertificates(url, scopedLote, LoTEFilterCriteria(LoTEServiceType.WALLET))
                        .mapNotNull { it.certificate }
                        .map { factory.generateCertificate(it.encodeToDer().inputStream()) as X509Certificate }
                        .toSet()
                    states[url] = State(anchors, validity.nextUpdate, minOf(now + 1.hours, validity.nextUpdate))
                    Napier.i("Loaded ${anchors.size} Wallet Provider anchors from $url")
                } catch (e: Exception) {
                    Napier.w("Could not refresh Wallet Provider LoTE from $url", e)
                }
            }
            return states.values.filter { clock.now() < it.expiry }.flatMap { it.anchors }.toSet()
        }
    }
}

private const val WALLET_LOTE_TYPE = "http://uri.etsi.org/19602/LoTEType/EUWalletProvidersList"
private const val WALLET_STATUS_APPROACH = "http://uri.etsi.org/19602/WalletProvidersList/StatusDetn/EU"
private const val WALLET_COMMUNITY_RULES = "http://uri.etsi.org/19602/WalletProvidersList/schemerules/EU"
private const val WALLET_ISSUANCE_TYPE = "http://uri.etsi.org/19602/SvcType/WalletSolution/Issuance"

private suspend fun fetchWalletProviderLote(url: String): String = withContext(Dispatchers.IO) {
    val request = HttpRequest.newBuilder(URI(url))
        .timeout(java.time.Duration.ofSeconds(15))
        .header("Accept", "application/json")
        .GET()
        .build()
    val response = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .build()
        .send(request, HttpResponse.BodyHandlers.ofString())
    require(response.statusCode() == 200) { "Wallet Provider LoTE returned HTTP ${response.statusCode()}" }
    response.body()
}
