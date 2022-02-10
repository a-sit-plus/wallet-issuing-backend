package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.NextMessage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.stub
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
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.random.Random

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.LOG_DEBUG)
class PupilIdControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var pupilIdService: PupilIdService

    @MockBean
    private lateinit var deviceBindingResponseValidator: DeviceBindingResponseValidator

    private lateinit var clientMessage: String
    private lateinit var serverMessage: String
    private lateinit var certificate: ByteArray
    private lateinit var bpk: String
    private lateinit var challengeResponse: String

    @BeforeEach
    fun beforeEach() {
        clientMessage = UUID.randomUUID().toString()
        serverMessage = UUID.randomUUID().toString()
        bpk = UUID.randomUUID().toString()
        certificate = Random.nextBytes(32)
        challengeResponse = UUID.randomUUID().toString()
        pupilIdService.stub {
            onBlocking { parseMessage(eq(clientMessage)) }.doReturn(NextMessage.Send(serverMessage, null))
        }
        whenever(deviceBindingResponseValidator.validate(eq(challengeResponse))).thenReturn(bpk)
    }

    @Test
    fun start_noAuthn_forbidden() = runTest {
        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = "does not matter"
        }.andExpect {
            status { isUnauthorized() }
        }.andReturn()
    }

    @Test
    @WithMockUser(authorities = ["DEVICE_BINDING"])
    fun start_withMockUser_ok() = runTest {
        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

    @Test
    fun start_challengeResponse_ok() = runTest {
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
        }.andReturn()
    }

}