package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.PkiService
import at.asitplus.wallet.backend.RevocationController
import at.asitplus.wallet.backend.RevocationService
import at.asitplus.wallet.backend.auth.ApiKeyAuthnService
import at.asitplus.wallet.lib.agent.Issuer
import kotlinx.coroutines.test.runTest
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.HttpRequestMethodNotSupportedException
import java.util.UUID

/**
 * This is no MockMVC test, because that test setup would not create the error documents
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["server.error.include-exception=true", "server.error.include-message=always"]
)
@AutoConfigureWebTestClient
class RevocationControllerErrorTest {

    @Autowired
    private lateinit var webClient: WebTestClient

    @MockBean
    private lateinit var revocationService: RevocationService

    @MockBean
    private lateinit var bindingStorageService: DeviceBindingStorageService

    @MockBean
    private lateinit var apiKeyAuthnService: ApiKeyAuthnService

    private lateinit var bpk: String
    private lateinit var deviceId: String
    private lateinit var apiKey: String
    private lateinit var exceptionMessage: String

    @BeforeEach
    fun setup() {
        bpk = UUID.randomUUID().toString()
        deviceId = UUID.randomUUID().toString()
        apiKey = UUID.randomUUID().toString()
        whenever(apiKeyAuthnService.validate(eq(apiKey)))
            .thenReturn("user")
        exceptionMessage = UUID.randomUUID().toString()
    }

    @Test
    fun `revoke binding returns error document`() {
        whenever(revocationService.revokeBinding(eq(bpk), eq(deviceId)))
            .thenThrow(IllegalArgumentException(exceptionMessage))

        webClient.post().uri("/revoke/binding")
            .bodyValue(RevocationController.RevocationRequest(bpk, deviceId))
            .header("X-API-Key", apiKey)
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody().jsonPath("status").isEqualTo(500)
            .jsonPath("exception").value(containsString(IllegalArgumentException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/revoke/binding")
            .jsonPath("message").isEqualTo(exceptionMessage)
    }

    @Test
    fun `revoke binding not found`() {
        whenever(revocationService.revokeBinding(eq(bpk), eq(deviceId)))
            .thenReturn(0)

        webClient.post().uri("/revoke/binding")
            .bodyValue(RevocationController.RevocationRequest(bpk, deviceId))
            .header("X-API-Key", apiKey)
            .exchange()
            .expectStatus().isNotFound
            .expectBody().isEmpty
    }

    @Test
    fun `revoke binding unauthorized`() {
        webClient.post().uri("/revoke/binding")
            .bodyValue(RevocationController.RevocationRequest(bpk, deviceId))
            .exchange()
            .expectStatus().isForbidden
            .expectBody().jsonPath("status").isEqualTo(403)
            .jsonPath("exception").value(containsString(AccessDeniedException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/revoke/binding")
    }

    @Test
    fun `revoke binding GET not allowed`() {
        webClient.get().uri("/revoke/binding")
            .exchange()
            .expectStatus().is4xxClientError
            .expectBody().jsonPath("status").isEqualTo(405)
            .jsonPath("exception").value(containsString(HttpRequestMethodNotSupportedException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/revoke/binding")
    }

    @Test
    fun `revoke pupilid returns error document`() {
        whenever(revocationService.revokeCredentialsByBpk(eq(bpk)))
            .thenThrow(IllegalArgumentException(exceptionMessage))

        webClient.post().uri("/revoke/pupilid")
            .bodyValue(RevocationController.RevocationRequest(bpk))
            .header("X-API-Key", apiKey)
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody().jsonPath("status").isEqualTo(500)
            .jsonPath("exception").value(containsString(IllegalArgumentException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/revoke/pupilid")
            .jsonPath("message").isEqualTo(exceptionMessage)
    }

    @Test
    fun `revoke pupilid returns error document for wrong request`() {
        webClient.post().uri("/revoke/pupilid")
            .bodyValue(RevocationController.RevocationRequest(bpk, deviceId))
            .header("X-API-Key", apiKey)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody().isEmpty
    }

    @Test
    fun `revoke pupilid not found`() {
        whenever(revocationService.revokeCredentialsByBpk(eq(bpk)))
            .thenReturn(0)

        webClient.post().uri("/revoke/pupilid")
            .bodyValue(RevocationController.RevocationRequest(bpk))
            .header("X-API-Key", apiKey)
            .exchange()
            .expectStatus().isNotFound
            .expectBody().isEmpty
    }

    @Test
    fun `revoke pupilid unauthorized`() {
        webClient.post().uri("/revoke/pupilid")
            .bodyValue(RevocationController.RevocationRequest(bpk))
            .exchange()
            .expectStatus().isForbidden
            .expectBody().jsonPath("status").isEqualTo(403)
            .jsonPath("exception").value(containsString(AccessDeniedException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/revoke/pupilid")
    }

    @Test
    fun `revoke pupilid GET not allowed`() {
        webClient.get().uri("/revoke/pupilid")
            .exchange()
            .expectStatus().is4xxClientError
            .expectBody().jsonPath("status").isEqualTo(405)
            .jsonPath("exception").value(containsString(HttpRequestMethodNotSupportedException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/revoke/pupilid")
    }

    @Test
    fun `devices returns error document`() {
        whenever(bindingStorageService.lookupDevices(eq(bpk)))
            .thenThrow(IllegalArgumentException(exceptionMessage))

        webClient.get().uri("/revoke/devices?bpk={bpk}", bpk)
            .header("X-API-Key", apiKey)
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody().jsonPath("status").isEqualTo(500)
            .jsonPath("exception").value(containsString(IllegalArgumentException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/revoke/devices")
            .jsonPath("message").isEqualTo(exceptionMessage)
    }

    @Test
    fun `devices returns error document for wrong request`() {
        webClient.get().uri("/revoke/devices/")
            .header("X-API-Key", apiKey)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody().jsonPath("status").isEqualTo(400)
            .jsonPath("path").isEqualTo("/revoke/devices/")
    }

    @Test
    fun `devices not found`() {
        whenever(bindingStorageService.lookupDevices(eq(bpk)))
            .thenReturn(listOf())

        webClient.get().uri("/revoke/devices?bpk={bpk}", bpk)
            .header("X-API-Key", apiKey)
            .exchange()
            .expectStatus().isNotFound
            .expectBody().isEmpty
    }

    @Test
    fun `devices unauthorized`() {
        webClient.get().uri("/revoke/devices?bpk={bpk}", bpk)
            .exchange()
            .expectStatus().isForbidden
            .expectBody().jsonPath("status").isEqualTo(403)
            .jsonPath("exception").value(containsString(AccessDeniedException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/revoke/devices")
    }

    @Test
    fun `devices POST not allowed`() {
        webClient.post().uri("/revoke/devices?bpk={bpk}", bpk)
            .exchange()
            .expectStatus().is4xxClientError
            .expectBody().jsonPath("status").isEqualTo(405)
            .jsonPath("exception").value(containsString(HttpRequestMethodNotSupportedException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/revoke/devices")
    }


}