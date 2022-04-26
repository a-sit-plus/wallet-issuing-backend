package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.decodeBase64ToArray
import at.asitplus.wallet.lib.encodeBase64
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.test.expect

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