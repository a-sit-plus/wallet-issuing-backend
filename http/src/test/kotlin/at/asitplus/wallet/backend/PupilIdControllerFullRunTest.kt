package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.DefaultCryptoService
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.IssueCredentialProtocolResult
import at.asitplus.wallet.lib.agent.NextMessage
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.decodeBase64ToArray
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertIs

/**
 * Simulates a full run of a client using the [PupilIdController].
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.LOG_DEBUG)
class PupilIdControllerFullRunTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var deviceBindingRepository: DeviceBindingRepository

    private lateinit var cryptoService: CryptoService
    private lateinit var subjectAgent: Agent
    private lateinit var subjectMessenger: IssueCredentialMessenger
    private lateinit var request: NextMessage.Send
    private lateinit var client: Client

    @BeforeEach
    fun beforeEach() {
        client = Client()
        cryptoService = DefaultCryptoService(keyPair = client.keyPair)
        subjectAgent = Agent(cryptoService = cryptoService)
        subjectMessenger = IssueCredentialMessenger(agent = subjectAgent, credentialScheme = ConstantIndex.PupilId)
        val bpk = UUID.randomUUID().toString()
        val deviceName = UUID.randomUUID().toString()
        val deviceId = UUID.randomUUID().toString()
        deviceBindingRepository.save(DeviceBinding(bpk, client.selfSignedCert.encoded, deviceName, deviceId))
    }

    @Test
    fun start_challengeResponse_ok() = runTest {
        request = subjectMessenger.startDirect() as NextMessage.Send

        val firstResponse = mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = request.message
        }.andExpect {
            status { isUnauthorized() }
            header { exists(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()

        val headerValue = firstResponse.response.getHeaderValue(HttpHeaders.WWW_AUTHENTICATE)
        val challenge = headerValue.toString().removePrefix("Challenge ").decodeBase64ToArray()!!
        val challengeResponse = client.answerBindingChallenge(challenge)

        val response = mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = request.message
            header(HttpHeaders.AUTHORIZATION, "Response $challengeResponse")
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val parsedMessage = subjectMessenger.parseMessage(response.response.contentAsString)
        assertIs<NextMessage.Result<IssueCredentialProtocolResult>>(parsedMessage)
    }

    @Test
    fun start_challengeDirectlyResponse_ok() = runTest {
        request = subjectMessenger.startDirect() as NextMessage.Send

        val firstResponse = mockMvc.post("/authn/devicebinding/challenge") {
        }.andExpect {
            status { isOk() }
            header { doesNotExist(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()

        val challenge = firstResponse.response.contentAsString.decodeBase64ToArray()!!
        val challengeResponse = client.answerBindingChallenge(challenge)

        val response = mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = request.message
            header(HttpHeaders.AUTHORIZATION, "Response $challengeResponse")
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val parsedMessage = subjectMessenger.parseMessage(response.response.contentAsString)
        assertIs<NextMessage.Result<IssueCredentialProtocolResult>>(parsedMessage)
    }

}