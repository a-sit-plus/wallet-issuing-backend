package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.DeviceBindingAuthnResult
import at.asitplus.wallet.backend.DeviceBindingAuthnService
import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.IssueCredentialAdapter
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.lib.agent.NextMessage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.random.Random

/**
 * Tests the Spring Security parts of the authentication for [PupilIdController],
 * i.e. it tests the filter, authentication provider, token and so on.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.LOG_DEBUG)
class PupilIdControllerSpringSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var issueCredentialAdapter: IssueCredentialAdapter

    @Autowired
    private lateinit var deviceBindingRepository: DeviceBindingRepository

    @MockBean
    private lateinit var deviceBindingStorageService: DeviceBindingStorageService

    @MockBean
    private lateinit var deviceBindingAuthnService: DeviceBindingAuthnService

    private lateinit var clientMessage: String
    private lateinit var serverMessage: String
    private lateinit var certificate: ByteArray
    private lateinit var deviceName: String
    private lateinit var deviceId: String
    private lateinit var bpk: String
    private lateinit var challengeResponse: String

    @BeforeEach
    fun beforeEach() {
        bpk = UUID.randomUUID().toString()
        certificate = Random.nextBytes(32)
        deviceName = UUID.randomUUID().toString()
        deviceId = UUID.randomUUID().toString()
        clientMessage = UUID.randomUUID().toString()
        serverMessage = UUID.randomUUID().toString()
        challengeResponse = UUID.randomUUID().toString()

        whenever(issueCredentialAdapter.parseMessage(eq(clientMessage)))
            .thenReturn(NextMessage.Send(serverMessage, null))
        whenever(deviceBindingAuthnService.validate(eq(challengeResponse)))
            .thenReturn(DeviceBindingAuthnResult(bpk, certificate))
        var deviceBinding = DeviceBinding(bpk, certificate, deviceName, deviceId)
        if (deviceBindingRepository.findByCertificateAndRevokedIsFalse(certificate) == null) {
            deviceBinding = deviceBindingRepository.save(deviceBinding)
        }
        whenever(deviceBindingStorageService.getDeviceBindingForCurrentUser())
            .thenReturn(deviceBinding)
    }

    @Test
    fun issue_noAuthn_forbidden() = runTest {
        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
        }.andExpect {
            status { isUnauthorized() }
        }.andReturn()
    }

    @Test
    @WithMockUser(authorities = ["DEVICE_BINDING"])
    fun issue_withMockUser_ok() = runTest {
        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

    @Test
    fun issue_challengeResponse_ok() = runTest {
        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
        }.andExpect {
            status { isUnauthorized() }
            header { exists(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()

        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
            header(HttpHeaders.AUTHORIZATION, "Response $challengeResponse")
        }.andExpect {
            status { isOk() }
            header { exists(X_AUTH_TOKEN) }
        }.andReturn()
    }

    @Test
    fun issue_getChallengeDirectlyResponse_ok() = runTest {
        mockMvc.get("/authn/devicebinding/challenge")
            .andExpect {
                status { isOk() }
                header { doesNotExist(HttpHeaders.WWW_AUTHENTICATE) }
            }.andReturn()

        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
            header(HttpHeaders.AUTHORIZATION, "Response $challengeResponse")
        }.andExpect {
            status { isOk() }
            header { exists(X_AUTH_TOKEN) }
        }.andReturn()
    }

    @Test
    fun issue_challengeResponse_sessionInvalid() = runTest {
        val firstResponse = mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
        }.andExpect {
            status { isUnauthorized() }
            header { exists(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()

        val xAuthToken = firstResponse.response.getHeaderValue(X_AUTH_TOKEN)!!

        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
            header(HttpHeaders.AUTHORIZATION, "Response $challengeResponse")
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isOk() }
            header { doesNotExist(X_AUTH_TOKEN) }
        }.andReturn()

        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isUnauthorized() }
        }.andReturn()
    }

    @Test
    fun issue_challengeResponse_invalid() = runTest {
        whenever(deviceBindingAuthnService.validate(eq(challengeResponse)))
            .thenReturn(null)

        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
        }.andExpect {
            status { isUnauthorized() }
            header { exists(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()

        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
            header(HttpHeaders.AUTHORIZATION, "Response $challengeResponse")
        }.andExpect {
            status { isUnauthorized() }
            header { exists(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()
    }

    companion object {
        private const val X_AUTH_TOKEN = "X-Auth-Token"
    }

}