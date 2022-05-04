package at.asitplus.wallet.backend

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.bouncycastle.cert.X509CRLHolder
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.junit.jupiter.api.Test
import java.util.Date
import java.util.UUID

class InMemoryPkiServiceTest {

    private val service = InMemoryPkiService()

    @Test
    fun `sign correct CSR`() {
        val subject = "CN=${UUID.randomUUID()}"
        val csr = Client().generateCsr(subject)

        val certificate = service.verifyAndSign(csr, subject)

        certificate.shouldNotBeNull()
    }

    @Test
    fun `do not sign CSR with wrong subject`() {
        val subject = "CN=${UUID.randomUUID()}"
        val csr = Client().generateCsr(subject)

        val certificate = service.verifyAndSign(csr, "CN=${UUID.randomUUID()}")

        certificate.shouldBeNull()
    }

    @Test
    fun `device binding certificates are valid`() {
        val subject = "CN=${UUID.randomUUID()}"
        val csr = Client().generateCsr(subject)
        val certificate = service.verifyAndSign(csr, subject)

        val verifierProvider =
            JcaContentVerifierProviderBuilder().build(X509CertificateHolder(service.getCaCertificate()))
        val holder = X509CertificateHolder(certificate)
        holder.isSignatureValid(verifierProvider) shouldBe true
        holder.isValidOn(Date()) shouldBe true
    }

    @Test
    fun `add revoked certificate to CRL`() {
        val subject = "CN=${UUID.randomUUID()}"
        val csr = Client().generateCsr(subject)
        val certificate = service.verifyAndSign(csr, subject)
        certificate.shouldNotBeNull()
        val serialNumber = X509CertificateHolder(certificate).serialNumber

        service.revokeCertificate(certificate)
        val crl = service.getCrl()

        val verifierProvider =
            JcaContentVerifierProviderBuilder().build(X509CertificateHolder(service.getCaCertificate()))
        val crlHolder = X509CRLHolder(crl)
        crlHolder.isSignatureValid(verifierProvider) shouldBe true
        val revokedCert = crlHolder.getRevokedCertificate(serialNumber)
        revokedCert.shouldNotBeNull()
    }

    @Test
    fun `do not add non-revoked certificate to CRL`() {
        val subject = "CN=${UUID.randomUUID()}"
        val csr = Client().generateCsr(subject)
        val certificate = service.verifyAndSign(csr, subject)
        certificate.shouldNotBeNull()
        val serialNumber = X509CertificateHolder(certificate).serialNumber

        val crl = service.getCrl()

        val revokedCert = X509CRLHolder(crl).getRevokedCertificate(serialNumber)
        revokedCert.shouldBeNull()
    }

}