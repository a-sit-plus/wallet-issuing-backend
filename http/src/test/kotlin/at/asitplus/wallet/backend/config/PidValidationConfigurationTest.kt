package at.asitplus.wallet.backend.config

import at.asitplus.wallet.lib.etsi.LoteProfile
import at.asitplus.wallet.lib.validation.TrustPurpose
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.DefaultResourceLoader
import java.nio.file.Path
import java.nio.file.Files
import java.util.Base64

class PidValidationConfigurationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `PID issuance and status use separate anchors without wallet or other credential fallback`() = runBlocking<Unit> {
        val issuer = MetadataTestPki()
        val status = MetadataTestPki()
        val service = TrustListService(TrustListConfiguration(
            stages = emptyList(), trustedCertificates = mapOf(
                LoteProfile.PID to TrustListCertificates(listOf(pem("issuer", issuer.intermediate)), listOf(pem("status", status.intermediate))),
            ),
        ), DefaultResourceLoader(), fetch = { error("must not fetch") })
        val anchors = PidValidationConfiguration().pidTrustAnchors(service)
        for (identifier in listOf("urn:eudi:pid:1", "eu.europa.ec.eudi.pid.1")) {
            anchors.resolve(identifier, TrustPurpose.ISSUANCE)!!.certificates shouldBe setOf(issuer.signumChain[1])
            anchors.resolve(identifier, TrustPurpose.STATUS)!!.certificates shouldBe setOf(status.signumChain[1])
        }
        anchors.resolve("org.iso.18013.5.1.mDL", TrustPurpose.ISSUANCE) shouldBe null
        anchors.resolve("urn:eudi:pid:unknown", TrustPurpose.ISSUANCE) shouldBe null
        val walletOnly = TrustListService(TrustListConfiguration(profiles = setOf(LoteProfile.WALLET), stages = emptyList()), DefaultResourceLoader())
        PidValidationConfiguration().pidTrustAnchors(walletOnly).resolve("urn:eudi:pid:1", TrustPurpose.ISSUANCE)!!.certificates shouldBe emptySet()
    }

    private fun pem(name: String, certificate: java.security.cert.X509Certificate) = directory.resolve("$name.pem").also {
        Files.writeString(it, "-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(certificate.encoded) + "\n-----END CERTIFICATE-----\n")
    }.toUri().toString()
}
