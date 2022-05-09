package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.DefaultCryptoService
import at.asitplus.wallet.lib.agent.HolderAgent
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.IssueCredentialProtocolResult
import at.asitplus.wallet.lib.agent.MessageWrapper
import at.asitplus.wallet.lib.agent.NextMessage
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.decodeBase64ToArray
import io.kotest.matchers.types.shouldBeInstanceOf
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

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

    private lateinit var holderCryptoService: CryptoService
    private lateinit var holderAgent: HolderAgent
    private lateinit var holderMessenger: IssueCredentialMessenger
    private lateinit var request: NextMessage.Send
    private lateinit var client: Client

    @BeforeEach
    fun beforeEach() {
        client = Client()
        holderCryptoService = DefaultCryptoService(keyPair = client.keyPair)
        holderAgent = HolderAgent.newDefaultInstance(cryptoService = holderCryptoService)
        holderMessenger = IssueCredentialMessenger.newHolderInstance(
            holder = holderAgent,
            credentialScheme = ConstantIndex.PupilId,
            keyId = holderCryptoService.keyId,
            messageWrapper = MessageWrapper(holderCryptoService)
        )
        val bpk = UUID.randomUUID().toString()
        val deviceName = UUID.randomUUID().toString()
        val deviceId = UUID.randomUUID().toString()
        deviceBindingRepository.save(
            DeviceBinding(
                bpk,
                client.selfSignedCert.encoded,
                deviceName,
                deviceId,
                client.selfSignedCert.notAfter.toInstant()
            )
        )
    }

    @Test
    fun start_challengeResponse_ok() = runTest {
        request = holderMessenger.startDirect() as NextMessage.Send

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

        val parsedMessage = holderMessenger.parseMessage(response.response.contentAsString)
        parsedMessage.shouldBeInstanceOf<NextMessage.Result<IssueCredentialProtocolResult>>()
    }

    @Test
    fun start_challengeDirectlyResponse_ok() = runTest {
        request = holderMessenger.startDirect() as NextMessage.Send

        val firstResponse = mockMvc.get("/authn/devicebinding/challenge")
            .andExpect {
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

        val parsedMessage = holderMessenger.parseMessage(response.response.contentAsString)
        parsedMessage.shouldBeInstanceOf<NextMessage.Result<IssueCredentialProtocolResult>>()
    }

}