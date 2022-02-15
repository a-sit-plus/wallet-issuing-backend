package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.MessageWrapper
import at.asitplus.wallet.lib.agent.NextMessage
import at.asitplus.wallet.lib.agent.ProblemReporter
import at.asitplus.wallet.lib.agent.SubjectCredentialStore
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertIs

/**
 * Tests the logic (the process) part of the [PupilIdController],
 * i.e. it skips the authentication process entirely by using [WithMockUser].
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.LOG_DEBUG)
class PupilIdControllerLogicTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val subjectCredentialStore = mock<SubjectCredentialStore>()
    private val subjectAgent = Agent(subjectCredentialStore = subjectCredentialStore)
    private val messageWrapper = MessageWrapper(subjectAgent.cryptoService)
    private val subjectMessenger = IssueCredentialMessenger(
        agent = subjectAgent,
        messageWrapper = messageWrapper,
        credentialScheme = ConstantIndex.PupilId
    )

    @Test
    @WithMockUser(authorities = ["DEVICE_BINDING"])
    fun issue_ok() = runTest {
        val request = subjectMessenger.startDirect()
        if (request !is NextMessage.Send) throw Exception("Internal Error")

        val response = mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = request.message
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val parsedMessage = subjectMessenger.parseMessage(response.response.contentAsString)
        assertIs<NextMessage.Result<*>>(parsedMessage)
        verify(subjectCredentialStore, times(1)).storeCredential(any(), any())
    }

    @Test
    @WithMockUser(authorities = ["DEVICE_BINDING"])
    fun issue_wrongMessage_badRequest() = runTest {
        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = UUID.randomUUID().toString()
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()

        verify(subjectCredentialStore, never()).storeCredential(any(), any())
    }

    @Test
    @WithMockUser(authorities = ["DEVICE_BINDING"])
    fun issue_problemReport_ok() = runTest {
        val problemReport = ProblemReporter().problemLastMessage("foo", "unknown")
        val message = messageWrapper.createSignedJwt(problemReport.message)

        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = message
        }.andExpect {
            status { isOk() }
            content { string("") }
        }.andReturn()

        verify(subjectCredentialStore, never()).storeCredential(any(), any())
    }

}