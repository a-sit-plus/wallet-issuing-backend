package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.TestTimeSource
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.lib.agent.*
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.decodeBase64ToArray
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.*

/**
 * Simulates a full run of a client using the [PupilIdController].
 */
@SpringBootTest
@AutoConfigureMockMvc
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
    private lateinit var bpk: String

    @BeforeEach
    fun beforeEach() {
        client = Client()
        holderCryptoService = DefaultCryptoService(keyPair = client.keyPair)
        holderAgent = HolderAgent.newDefaultInstance(
            cryptoService = holderCryptoService,
            clock= TestTimeSource,
        )
        holderMessenger = IssueCredentialMessenger.newHolderInstance(
            holder = holderAgent,
            credentialScheme = ConstantIndex.PupilId,
            keyId = holderCryptoService.keyId,
            messageWrapper = MessageWrapper(holderCryptoService)
        )
        bpk = UUID.randomUUID().toString()
        client.storeDeviceBinding(bpk, deviceBindingRepository)
    }

    @Test
    fun `issue serves challenge, correct response leads to success`() = runTest {
        request = holderMessenger.startDirect() as NextMessage.Send

        val firstResponse = mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = request.message
        }.andExpect {
            status { isUnauthorized() }
            header { exists(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()

        val challengeResponse = answerChallenge(firstResponse)

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
    fun `issue with response from challenge from direct endpoint`() = runTest {
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

    @Test
    fun `response with expired device binding leads to unauthorized`() = runTest {
        deviceBindingRepository.deleteAll()
        DeviceBinding(
            bpk,
            client.selfSignedCert.encoded,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            Instant.now().minusSeconds(1)
        ).also {
            deviceBindingRepository.save(it)
        }

        request = holderMessenger.startDirect() as NextMessage.Send

        val firstResponse = mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = request.message
        }.andExpect {
            status { isUnauthorized() }
            header { exists(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()

        val challengeResponse = answerChallenge(firstResponse)

        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = request.message
            header(HttpHeaders.AUTHORIZATION, "Response $challengeResponse")
        }.andExpect {
            status { isUnauthorized() }
            header { exists(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()
    }

    private fun answerChallenge(response: MvcResult): String {
        val headerValue = response.response.getHeaderValue(HttpHeaders.WWW_AUTHENTICATE)
        val challenge = headerValue.toString().removePrefix("Challenge ").decodeBase64ToArray()!!
        return client.answerBindingChallenge(challenge)
    }

}