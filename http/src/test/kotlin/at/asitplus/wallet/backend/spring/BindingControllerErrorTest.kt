package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.service.BindingService
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.pupilid.BindingConfirmRequestJ
import at.asitplus.wallet.pupilid.BindingCsrRequestJ
import at.asitplus.wallet.pupilid.BindingParamsRequestJ
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.HttpRequestMethodNotSupportedException
import java.util.UUID

/**
 * This is no MockMVC test, because that test setup would not create the error documents
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["server.error.include-exception=true", "server.error.include-message=always", "backend.authn.device-binding.attestation.noop=true"]
)
@AutoConfigureWebTestClient(timeout = "PT1M")
class BindingControllerErrorTest {

    @Autowired
    private lateinit var webClient: WebTestClient

    @MockBean
    private lateinit var bindingService: BindingService

    @MockBean
    private lateinit var extNonceAuthnService: ExtNonceAuthnService

    private lateinit var nonce: String
    private lateinit var bpk: String
    private lateinit var exceptionMessage: String

    @BeforeEach
    fun setup() {
        nonce = UUID.randomUUID().toString()
        bpk = UUID.randomUUID().toString()
        whenever( runBlocking {extNonceAuthnService.exchangeNonceForBpk(eq(nonce))}).thenReturn(bpk)
        exceptionMessage = UUID.randomUUID().toString()
    }

    @Test
    fun `binding start returns error document`() {
        whenever(bindingService.getBindingParams(any()))
            .thenThrow(IllegalArgumentException(exceptionMessage))

        webClient.post().uri("/binding/start")
            .bodyValue(BindingParamsRequestJ("deviceName"))
            .header("X-Auth-ExtNonce", nonce)
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody().jsonPath("status").isEqualTo(500)
            .jsonPath("exception").value(containsString(IllegalArgumentException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/binding/start")
            .jsonPath("message").isEqualTo(exceptionMessage)
    }

    @Test
    fun `binding start unauthorized`() {
        webClient.post().uri("/binding/start")
            .bodyValue(BindingParamsRequestJ("deviceName"))
            .exchange()
            .expectStatus().isForbidden
            .expectBody().jsonPath("status").isEqualTo(403)
            .jsonPath("exception").value(containsString(AccessDeniedException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/binding/start")
    }

    @Test
    fun `binding start GET not allowed`() {
        webClient.get().uri("/binding/start")
            .exchange()
            .expectStatus().is4xxClientError
            .expectBody().jsonPath("status").isEqualTo(405)
            .jsonPath("exception").value(containsString(HttpRequestMethodNotSupportedException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/binding/start")
    }

    @Test
    fun `binding create returns error document`() {
        whenever(bindingService.signCertificate(any(), any(), any(), any(), any()))
            .thenThrow(IllegalArgumentException(exceptionMessage))

        webClient.post().uri("/binding/create")
            .bodyValue(BindingCsrRequestJ(byteArrayOf(), byteArrayOf(), "deviceName", listOf()))
            .header("X-Auth-ExtNonce", nonce)
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody().jsonPath("status").isEqualTo(500)
            .jsonPath("exception").value(containsString(IllegalArgumentException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/binding/create")
            .jsonPath("message").isEqualTo(exceptionMessage)
    }

    @Test
    fun `binding create returns error document 400`() {
        whenever(bindingService.signCertificate(any(), any(), any(), any(), any()))
            .thenReturn(null)

        webClient.post().uri("/binding/create")
            .bodyValue(BindingCsrRequestJ(byteArrayOf(), byteArrayOf(), "deviceName", listOf()))
            .header("X-Auth-ExtNonce", nonce)
            .exchange()
            .expectStatus().is4xxClientError
            .expectBody().jsonPath("status").isEqualTo(400)
            .jsonPath("path").isEqualTo("/binding/create")
    }

    @Test
    fun `binding create unauthorized`() {
        webClient.post().uri("/binding/create")
            .bodyValue(BindingCsrRequestJ(byteArrayOf(), byteArrayOf(), "deviceName", listOf()))
            .exchange()
            .expectStatus().isForbidden
            .expectBody().jsonPath("status").isEqualTo(403)
            .jsonPath("exception").value(containsString(AccessDeniedException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/binding/create")
    }

    @Test
    fun `binding create GET not allowed`() {
        webClient.get().uri("/binding/create")
            .exchange()
            .expectStatus().is4xxClientError
            .expectBody().jsonPath("status").isEqualTo(405)
            .jsonPath("exception").value(containsString(HttpRequestMethodNotSupportedException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/binding/create")
    }

    @Test
    fun `binding confirm returns error document`() {
        whenever(bindingService.confirm(any()))
            .thenThrow(IllegalArgumentException(exceptionMessage))

        webClient.post().uri("/binding/confirm")
            .bodyValue(BindingConfirmRequestJ(true))
            .header("X-Auth-ExtNonce", nonce)
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody().jsonPath("status").isEqualTo(500)
            .jsonPath("exception").value(containsString(IllegalArgumentException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/binding/confirm")
            .jsonPath("message").isEqualTo(exceptionMessage)
    }

    @Test
    fun `binding confirm returns error document for success not set`() {
        whenever(bindingService.confirm(eq(false)))
            .thenReturn(null)

        webClient.post().uri("/binding/confirm")
            .bodyValue(BindingConfirmRequestJ(false))
            .header("X-Auth-ExtNonce", nonce)
            .exchange()
            .expectStatus().is4xxClientError
            .expectBody().jsonPath("status").isEqualTo(400)
            .jsonPath("path").isEqualTo("/binding/confirm")
            .jsonPath("message").isEqualTo("success not set")
    }

    @Test
    fun `binding confirm unauthorized`() {
        webClient.post().uri("/binding/confirm")
            .bodyValue(BindingConfirmRequestJ(true))
            .exchange()
            .expectStatus().isForbidden
            .expectBody().jsonPath("status").isEqualTo(403)
            .jsonPath("exception").value(containsString(AccessDeniedException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/binding/confirm")
    }

    @Test
    fun `binding confirm GET not allowed`() {
        webClient.get().uri("/binding/confirm")
            .exchange()
            .expectStatus().is4xxClientError
            .expectBody().jsonPath("status").isEqualTo(405)
            .jsonPath("exception").value(containsString(HttpRequestMethodNotSupportedException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/binding/confirm")
    }

}