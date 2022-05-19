package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.PkiService
import at.asitplus.wallet.lib.agent.Issuer
import kotlinx.coroutines.test.runTest
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

/**
 * This is no MockMVC test, because that test setup would not create the error documents
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["server.error.include-exception=true", "server.error.include-message=always"]
)
@AutoConfigureWebTestClient
class PublicControllerErrorTest {

    @Autowired
    private lateinit var webClient: WebTestClient

    @MockBean
    private lateinit var issuer: Issuer

    @MockBean
    private lateinit var pkiService: PkiService

    private lateinit var exceptionMessage: String

    @BeforeEach
    fun setup() {
        exceptionMessage = UUID.randomUUID().toString()
    }

    @Test
    fun `GET VC status list returns error document`() {
        runTest {
            whenever(issuer.issueRevocationListCredential()).thenThrow(IllegalArgumentException(exceptionMessage))
        }

        webClient.get().uri("/credentials/status/1").exchange()
            .expectStatus().is5xxServerError
            .expectBody().jsonPath("status").isEqualTo(500)
            .jsonPath("exception").value(containsString(IllegalArgumentException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/credentials/status/1")
            .jsonPath("message").isEqualTo(exceptionMessage)
    }

    @Test
    fun `GET CRL returns not found`() {
        whenever(pkiService.getCrl()).thenReturn(null)

        webClient.get().uri("/crl/1").exchange()
            .expectStatus().isNotFound
            .expectBody().jsonPath("status").isEqualTo(404)
            .jsonPath("path").isEqualTo("/crl/1")
    }

    @Test
    fun `GET CRL returns error document`() {
        whenever(pkiService.getCrl()).thenThrow(IllegalArgumentException(exceptionMessage))

        webClient.get().uri("/crl/1").exchange()
            .expectStatus().is5xxServerError
            .expectBody().jsonPath("status").isEqualTo(500)
            .jsonPath("exception").value(containsString(IllegalArgumentException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/crl/1")
            .jsonPath("message").isEqualTo(exceptionMessage)
    }

    @Test
    fun `GET CA returns not found`() {
        whenever(pkiService.getCaCertificate()).thenReturn(null)

        webClient.get().uri("/ca/1").exchange()
            .expectStatus().isNotFound
            .expectBody().jsonPath("status").isEqualTo(404)
            .jsonPath("path").isEqualTo("/ca/1")
    }

    @Test
    fun `GET CA returns error document`() {
        whenever(pkiService.getCaCertificate()).thenThrow(IllegalArgumentException(exceptionMessage))

        webClient.get().uri("/ca/1").exchange()
            .expectStatus().is5xxServerError
            .expectBody().jsonPath("status").isEqualTo(500)
            .jsonPath("exception").value(containsString(IllegalArgumentException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/ca/1")
            .jsonPath("message").isEqualTo(exceptionMessage)
    }

}