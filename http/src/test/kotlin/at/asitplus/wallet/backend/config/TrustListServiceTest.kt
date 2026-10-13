package at.asitplus.wallet.backend.config

import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.wallet.lib.etsi.LoTEStage
import at.asitplus.wallet.lib.etsi.LoteProfile
import at.asitplus.wallet.lib.jws.JwsHeaderCertOrJwk
import at.asitplus.wallet.lib.jws.SignJwt
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.DefaultResourceLoader
import java.nio.file.Path
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class TrustListServiceTest {
    @TempDir lateinit var directory: Path
    private val resources = DefaultResourceLoader()

    @Test
    fun `configured profiles and stages select URLs with explicit A-SIT wallet and PID scopes`() = runBlocking<Unit> {
        val fetched = mutableListOf<String>()
        val service = TrustListService(TrustListConfiguration(
            profiles = setOf(LoteProfile.WALLET, LoteProfile.PID), stages = listOf(LoTEStage.ACCEPTANCE),
        ), resources, fetch = { fetched += it; error("offline") })
        val wallet = service.issuanceAnchors(LoteProfile.WALLET)
        check(wallet.isNotEmpty())
        service.revocationAnchors(LoteProfile.WALLET) shouldBe wallet
        service.issuanceAnchors(LoteProfile.PID) shouldBe wallet
        service.revocationAnchors(LoteProfile.PID) shouldBe wallet
        service.issuanceAnchors(LoteProfile.mDL) shouldBe emptySet()
        fetched.toSet() shouldBe setOf(LoteProfile.WALLET.fetchUrl(LoTEStage.ACCEPTANCE), LoteProfile.PID.fetchUrl(LoTEStage.ACCEPTANCE))
        Unit
    }

    @Test
    fun `custom issuance and status anchors are separate and disabled profiles authorize nothing`() = runBlocking<Unit> {
        val issuance = MetadataTestPki()
        val status = MetadataTestPki()
        val config = TrustListConfiguration(stages = emptyList(), trustedCertificates = mapOf(
            LoteProfile.WALLET to TrustListCertificates(listOf(pem("issuance", issuance.intermediate)), listOf(pem("status", status.intermediate))),
        ))
        val service = TrustListService(config, resources, fetch = { error("must not fetch") })
        service.issuanceAnchors(LoteProfile.WALLET) shouldBe setOf(issuance.signumChain[1])
        service.revocationAnchors(LoteProfile.WALLET) shouldBe setOf(status.signumChain[1])
        val disabled = TrustListService(config.copy(profiles = emptySet()), resources)
        disabled.issuanceAnchors(LoteProfile.WALLET) shouldBe emptySet()
        disabled.revocationAnchors(LoteProfile.WALLET) shouldBe emptySet()
        val wallet = WalletProviderTrustService(WalletAttestationConfiguration(
            trustedCertificates = listOf(pem("legacy-wallet", issuance.intermediate)),
            trustedKeyAttestationCertificates = listOf(pem("legacy-ka", issuance.intermediate)),
        ), resources, disabled)
        wallet.walletProviderAnchors() shouldBe emptySet()
        wallet.keyAttestationAnchors() shouldBe emptySet()
    }

    @Test
    fun `signed wallet list separates services and drops expired lists after failed refresh`() = runBlocking<Unit> {
        val issuer = MetadataTestPki()
        val status = MetadataTestPki()
        val clock = MutableClock(Clock.System.now())
        val token = listToken(issuer, status, clock.now())
        var fetches = 0
        val service = TrustListService(TrustListConfiguration(profiles = setOf(LoteProfile.WALLET), stages = listOf(LoTEStage.ACCEPTANCE),
            signerCertificates = listOf(pem("list-signer", issuer.intermediate)), trustedCertificates = emptyMap()), resources,
            fetch = { fetches++; if (fetches == 1) token else error("offline") }, clock = clock)
        service.issuanceAnchors(LoteProfile.WALLET) shouldBe setOf(issuer.signumChain[1])
        service.revocationAnchors(LoteProfile.WALLET) shouldBe setOf(status.signumChain[1])
        fetches shouldBe 1
        clock.time += 2.hours
        service.issuanceAnchors(LoteProfile.WALLET) shouldBe emptySet()
        service.revocationAnchors(LoteProfile.WALLET) shouldBe emptySet()
    }

    @Test
    fun `wrong profile and unauthorized list signer contribute no anchors`() = runBlocking<Unit> {
        val issuer = MetadataTestPki()
        val token = listToken(issuer, MetadataTestPki(), Clock.System.now())
        val config = TrustListConfiguration(profiles = setOf(LoteProfile.PID), stages = listOf(LoTEStage.ACCEPTANCE), trustedCertificates = emptyMap())
        TrustListService(config, resources, fetch = { token }).issuanceAnchors(LoteProfile.PID) shouldBe emptySet()
        TrustListService(config.copy(profiles = setOf(LoteProfile.WALLET), signerCertificates = listOf(pem("wrong", MetadataTestPki().root))),
            resources, fetch = { token }).issuanceAnchors(LoteProfile.WALLET) shouldBe emptySet()
    }

    @Test
    fun `insecure override URLs fail startup`() {
        shouldThrow<IllegalArgumentException> {
            TrustListService(TrustListConfiguration(urls = mapOf(LoteProfile.WALLET to listOf("http://example/list"))), resources)
        }
    }

    private fun pem(name: String, certificate: java.security.cert.X509Certificate): String = directory.resolve("$name.pem").also {
        java.nio.file.Files.writeString(it, "-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(certificate.encoded) + "\n-----END CERTIFICATE-----\n")
    }.toUri().toString()

    private suspend fun listToken(issuer: MetadataTestPki, status: MetadataTestPki, now: Instant): String {
        val issuedAt = Instant.fromEpochSeconds(now.epochSeconds)
        val wallet = LoteProfile.WALLET
        val name = "[{\"lang\":\"en\",\"value\":\"Test Provider\"}]"
        fun service(type: String, cert: java.security.cert.X509Certificate) = """{"ServiceInformation":{
            "ServiceName":$name,"ServiceTypeIdentifier":"$type",
            "ServiceDigitalIdentity":{"X509Certificates":[{"val":"${Base64.getEncoder().encodeToString(cert.encoded)}"}]}}}"""
        val payload = joseCompliantSerializer.parseToJsonElement("""{"LoTE":{
            "ListAndSchemeInformation":{"LoTEVersionIdentifier":1,"LoTESequenceNumber":1,
            "ListIssueDateTime":"$issuedAt","NextUpdate":"${issuedAt + 1.hours}","SchemeOperatorName":$name,
            "LoTEType":"${wallet.loteType}","StatusDeterminationApproach":"${wallet.statusDeterminationApproach}",
            "SchemeTypeCommunityRules":[{"lang":"en","uriValue":"${wallet.schemeCommunityRules.single()}"}],"SchemeTerritory":"EU"},
            "TrustedEntitiesList":[{"TrustedEntityInformation":{"TEName":$name,
            "TEAddress":{"TEPostalAddress":[{"lang":"en","StreetAddress":"Test","Locality":"Vienna","PostalCode":"1010","Country":"AT"}],
            "TEElectronicAddress":[{"lang":"en","uriValue":"mailto:test@example.org"}]},
            "TEInformationURI":[{"lang":"en","uriValue":"https://example.org"}]},
            "TrustedEntityServices":[${service(wallet.serviceTypeIdentifierIssuance, issuer.intermediate)},${service(wallet.serviceTypeIdentifierRevocation, status.intermediate)}]}]}}""") as JsonObject
        return SignJwt<JsonObject>(issuer.keyMaterial, JwsHeaderCertOrJwk())("lote+jwt", payload, JsonObject.serializer()).getOrThrow().jws.toString()
    }

    private class MutableClock(var time: Instant) : Clock { override fun now() = time }
}
