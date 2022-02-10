package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.MessageWrapper
import at.asitplus.wallet.lib.agent.NextMessage
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.encodeBase64
import at.asitplus.wallet.lib.jvm.AgentJvm
import at.asitplus.wallet.lib.jvm.JwsServiceJvm
import at.asitplus.wallet.lib.jvm.KeyIdServiceJvm
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.random.Random
import kotlin.test.assertIs

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.LOG_DEBUG)
class PupilIdControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val subjectAgent = AgentJvm.new()
    private val subjectMessenger = IssueCredentialMessenger(
        agent = subjectAgent,
        messageWrapper = MessageWrapper(subjectAgent.cryptoService, KeyIdServiceJvm(), JwsServiceJvm()),
        credentialScheme = ConstantIndex.PupilId
    )

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
        val request = subjectMessenger.startDirect()
        if (request !is NextMessage.Send) throw Exception("Internal Error")

        val response = mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = request.message
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val parsedMessage = subjectMessenger.parseMessage(response.response.contentAsString)
        assertIs<NextMessage.Finished>(parsedMessage)
    }

    @Test
    fun start_challengeResponse_ok() = runTest {
        val request = subjectMessenger.startDirect()
        if (request !is NextMessage.Send) throw Exception("Internal Error")

        val firstResponse = mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = request.message
        }.andExpect {
            status { isUnauthorized() }
            header { exists("WWW-Authenticate") }
        }.andReturn()

        val challenge = firstResponse.response.getHeaderValue("WWW-Authenticate").toString().removePrefix("Challenge ")
        val challengeResponse = Random.nextBytes(32).encodeBase64()

        val response = mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = request.message
            header("Authorization", "Response $challengeResponse")
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val parsedMessage = subjectMessenger.parseMessage(response.response.contentAsString)
        assertIs<NextMessage.Finished>(parsedMessage)
    }

}