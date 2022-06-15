package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.DeviceBindingAuthnResult
import at.asitplus.wallet.backend.DeviceBindingAuthnService
import at.asitplus.wallet.backend.IssueCredentialAdapter
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.pupilid.BindingParamsRequestJ
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
@AutoConfigureWebTestClient(timeout = "PT1M")
class PupilIdControllerErrorTest {

    @Autowired
    private lateinit var webClient: WebTestClient

    @Autowired
    private lateinit var deviceBindingRepository: DeviceBindingRepository

    @MockBean
    private lateinit var issueCredentialAdapter: IssueCredentialAdapter

    @MockBean
    private lateinit var authenticationSupplier: AuthenticationSupplier

    @MockBean
    private lateinit var deviceBindingAuthnService: DeviceBindingAuthnService

    private lateinit var bpk: String
    private lateinit var certificate: ByteArray
    private lateinit var challengeResponse: String
    private lateinit var exceptionMessage: String

    @BeforeEach
    fun setup() {
        val client = Client()
        bpk = UUID.randomUUID().toString()
        certificate = client.selfSignedCert.encoded
        challengeResponse = UUID.randomUUID().toString()
        whenever(deviceBindingAuthnService.validate(eq(challengeResponse)))
            .thenReturn(DeviceBindingAuthnResult(bpk, certificate))
        client.storeDeviceBinding(bpk, deviceBindingRepository)
        whenever(authenticationSupplier.getCurrentUserCertificate())
            .thenReturn(certificate)
        exceptionMessage = UUID.randomUUID().toString()
    }

    @Test
    fun `pupilid issue returns error document`() {
        whenever(issueCredentialAdapter.parseMessage(any()))
            .thenThrow(IllegalArgumentException(exceptionMessage))

        webClient.post().uri("/pupilid/issue")
            .bodyValue("foo")
            .header(HttpHeaders.AUTHORIZATION, "Response $challengeResponse")
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody().jsonPath("status").isEqualTo(500)
            .jsonPath("exception").value(containsString(IllegalArgumentException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/pupilid/issue")
            .jsonPath("message").isEqualTo(exceptionMessage)
    }

    @Test
    fun `pupilid issue unauthorized`() {
        webClient.post().uri("/pupilid/issue")
            .bodyValue("foo")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody().jsonPath("status").isEqualTo(401)
            .jsonPath("exception").value(containsString(AccessDeniedException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/pupilid/issue")
    }

    @Test
    fun `pupilid issue wrong authorization`() {
        webClient.post().uri("/pupilid/issue")
            .bodyValue("foo")
            .header(HttpHeaders.AUTHORIZATION, "Response ${challengeResponse.reversed()}")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody().jsonPath("status").isEqualTo(401)
            .jsonPath("exception").value(containsString(AccessDeniedException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/pupilid/issue")
    }

    @Test
    fun `pupilid issue GET not allowed`() {
        webClient.get().uri("/pupilid/issue")
            .exchange()
            .expectStatus().is4xxClientError
            .expectBody().jsonPath("status").isEqualTo(405)
            .jsonPath("exception").value(containsString(HttpRequestMethodNotSupportedException::class.java.simpleName))
            .jsonPath("path").isEqualTo("/pupilid/issue")
    }


}