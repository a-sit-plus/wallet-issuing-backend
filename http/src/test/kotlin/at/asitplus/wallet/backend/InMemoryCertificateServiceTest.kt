package at.asitplus.wallet.backend

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import org.bouncycastle.cert.X509CRLHolder
import org.bouncycastle.cert.X509CertificateHolder
import org.junit.jupiter.api.Test
import java.util.UUID

class InMemoryCertificateServiceTest {

    private val service = InMemoryCertificateService()

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
    fun `add revoked certificate to CRL`() {
        val subject = "CN=${UUID.randomUUID()}"
        val csr = Client().generateCsr(subject)
        val certificate = service.verifyAndSign(csr, subject)
        certificate.shouldNotBeNull()
        val serialNumber = X509CertificateHolder(certificate).serialNumber

        service.revokeCertificate(certificate)
        val crl = service.buildCrl()

        val revokedCert = X509CRLHolder(crl).getRevokedCertificate(serialNumber)
        revokedCert.shouldNotBeNull()
    }

    @Test
    fun `do not add non-revoked certificate to CRL`() {
        val subject = "CN=${UUID.randomUUID()}"
        val csr = Client().generateCsr(subject)
        val certificate = service.verifyAndSign(csr, subject)
        certificate.shouldNotBeNull()
        val serialNumber = X509CertificateHolder(certificate).serialNumber

        val crl = service.buildCrl()

        val revokedCert = X509CRLHolder(crl).getRevokedCertificate(serialNumber)
        revokedCert.shouldBeNull()
    }

}