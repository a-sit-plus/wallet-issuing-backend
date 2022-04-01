package at.asitplus.wallet.backend

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.UUID

class InMemoryCertificateServiceTest {

    private val service = InMemoryCertificateService()

    @Test
    fun success() {
        val subject = "CN=${UUID.randomUUID()}"
        val keyPair = KeyPairGenerator.getInstance("EC").generateKeyPair()!!
        val csr = generateCsr(keyPair, subject)

        val certificate = service.verifyAndSign(csr, subject)

        certificate.shouldNotBeNull()
    }

    @Test
    fun `wrong subject`() {
        val subject = "CN=${UUID.randomUUID()}"
        val keyPair = KeyPairGenerator.getInstance("EC").generateKeyPair()!!
        val csr = generateCsr(keyPair, subject)

        val certificate = service.verifyAndSign(csr, "CN=${UUID.randomUUID()}")

        certificate.shouldBeNull()
    }

    private fun generateCsr(keyPair: KeyPair, subject: String): ByteArray {
        return JcaPKCS10CertificationRequestBuilder(X500Name(subject), keyPair.public).build(
            JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
        ).encoded
    }

}