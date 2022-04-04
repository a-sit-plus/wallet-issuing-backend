package at.asitplus.wallet.backend

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.Test
import java.util.UUID

class InMemoryCertificateServiceTest {

    private val service = InMemoryCertificateService()

    @Test
    fun success() {
        val subject = "CN=${UUID.randomUUID()}"
        val csr = Client().generateCsr(subject)

        val certificate = service.verifyAndSign(csr, subject)

        certificate.shouldNotBeNull()
    }

    @Test
    fun `wrong subject`() {
        val subject = "CN=${UUID.randomUUID()}"
        val csr = Client().generateCsr(subject)

        val certificate = service.verifyAndSign(csr, "CN=${UUID.randomUUID()}")

        certificate.shouldBeNull()
    }

}