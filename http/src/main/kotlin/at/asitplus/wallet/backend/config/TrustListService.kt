package at.asitplus.wallet.backend.config

import at.asitplus.etsi.ListOfTrustedEntities
import at.asitplus.etsi.TrustListPayload
import at.asitplus.signum.indispensable.josef.JwsCompact
import at.asitplus.wallet.lib.etsi.LoTEFilterService
import at.asitplus.wallet.lib.etsi.LoTETrustAnchorProvider
import at.asitplus.wallet.lib.etsi.LoteProfile
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import at.asitplus.signum.indispensable.pki.X509Certificate

/** Fetches authenticated, current LoTEs and separates issuance/status anchors for each enabled profile. */
class TrustListService(
    private val configuration: TrustListConfiguration,
    resourceLoader: ResourceLoader,
    private val fetch: suspend (String) -> String = ::fetchTrustList,
    private val clock: Clock = Clock.System,
) {
    val urlsByProfile = configuration.profiles.associateWith { profile ->
        (configuration.urls[profile] ?: configuration.stages.map { profile.fetchUrl(it) }).distinct().also { urls ->
            urls.forEach { require(URI(it).let { uri -> uri.scheme == "https" && uri.host != null }) { "LoTE URL must use HTTPS" } }
        }
    }
    private val customAnchors = configuration.trustedCertificates.filterKeys { it in configuration.profiles }.mapValues { (_, certificates) ->
        fun load(locations: List<String>) = loadAttestationAnchors(locations, resourceLoader).map {
            requireNotNull(X509Certificate.decodeFromByteArray(it.encoded))
        }.toSet()
        load(certificates.issuance) to load(certificates.status)
    }
    private val signerAnchors = loadAttestationAnchors(configuration.signerCertificates, resourceLoader)
    private val verifier = VerifyJwsObjectJades(
        verifyJwsObject = if (signerAnchors.isEmpty()) at.asitplus.wallet.lib.jws.VerifyJwsObject()
        else trustedAttestationVerifier { signerAnchors },
    )
    private data class State(
        val list: ListOfTrustedEntities? = null,
        val expiry: Instant = Instant.DISTANT_PAST,
        val nextRefresh: Instant = Instant.DISTANT_PAST,
    )
    private val states = urlsByProfile.flatMap { (profile, urls) -> urls.map { (profile to it) to State() } }.toMap().toMutableMap()
    private val mutex = Mutex()
    private val filter = LoTEFilterService()
    private val provider = LoTETrustAnchorProvider(trustLists = { currentLists() })

    suspend fun issuanceAnchors(profile: LoteProfile): Set<X509Certificate> =
        if (profile !in configuration.profiles) emptySet()
        else provider.issuanceAnchors(profile).toSet() + customAnchors[profile]?.first.orEmpty()

    suspend fun revocationAnchors(profile: LoteProfile): Set<X509Certificate> =
        if (profile !in configuration.profiles) emptySet()
        else provider.revocationAnchors(profile).toSet() + customAnchors[profile]?.second.orEmpty()

    private suspend fun currentLists(): Collection<ListOfTrustedEntities> = mutex.withLock {
        states.keys.forEach { key ->
            val (profile, url) = key
            val now = clock.now()
            val current = states.getValue(key)
            if (now >= current.nextRefresh) {
                states[key] = current.copy(nextRefresh = now + 5.minutes)
                try {
                    val (jws, payload) = JwsCompact.parse<TrustListPayload>(fetch(url)).getOrThrow()
                    verifier(jws).getOrThrow()
                    val list = payload.loTe
                    require(filter.profileOf(list) == profile) { "LoTE does not match configured profile $profile" }
                    val validity = requireNotNull(list.listAndSchemeInformation)
                    val expiry = requireNotNull(validity.nextUpdate) { "LoTE has no nextUpdate" }
                    require(validity.listIssueDateTime <= now && now < expiry) { "LoTE is not currently valid" }
                    states[key] = State(list, expiry, minOf(now + 1.hours, expiry))
                    Napier.i("Loaded $profile LoTE from $url")
                } catch (e: Exception) {
                    Napier.w("Could not refresh $profile LoTE from $url", e)
                }
            }
        }
        states.values.filter { clock.now() < it.expiry }.mapNotNull { it.list }
    }
}

internal suspend fun fetchTrustList(url: String): String = withContext(Dispatchers.IO) {
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
