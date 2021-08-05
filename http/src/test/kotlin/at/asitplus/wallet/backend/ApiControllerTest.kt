package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.MessageWrapper
import at.asitplus.wallet.lib.agent.NextMessageFinished
import at.asitplus.wallet.lib.agent.NextMessageToSend
import at.asitplus.wallet.lib.msg.IssueCredential
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertIs
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class ApiControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var issueCredentialMessenger: IssueCredentialMessenger

    private lateinit var subject: Agent

    private lateinit var subjectIssueCredentialMessenger: IssueCredentialMessenger

    @BeforeEach
    fun beforeEach() {
        subject = Agent()
        subjectIssueCredentialMessenger =
            IssueCredentialMessenger(subject, MessageWrapper(subject.cryptoService), "https://example.com/issue")
    }

    @Test
    fun issue_wrongMessage_400() {
        runBlocking {
            val requestCredentialMessage = subjectIssueCredentialMessenger.start()

            mockMvc.post("/issue") {
                content = requestCredentialMessage
            }.andExpect {
                status { isBadRequest() }
            }.andReturn()
        }
    }

    @Test
    fun issue_wrongInvitation_400() {
        runBlocking {
            val agent = Agent()
            val oobInvitation =
                IssueCredentialMessenger(
                    Agent(),
                    MessageWrapper(agent.cryptoService),
                    "https://example.com/issue"
                ).start()

            val requestCredentialMessage = subjectIssueCredentialMessenger.parseMessage(oobInvitation)
            assertIs<NextMessageToSend>(requestCredentialMessage)

            mockMvc.post("/issue") {
                content = requestCredentialMessage.message
            }.andExpect {
                status { isBadRequest() }
            }.andReturn()
        }
    }

    @Test
    fun issue_success() {
        runBlocking {
            val oobInvitation = issueCredentialMessenger.start()

            val requestCredentialMessage = subjectIssueCredentialMessenger.parseMessage(oobInvitation)
            assertIs<NextMessageToSend>(requestCredentialMessage)

            val result = mockMvc.post("/issue") {
                content = requestCredentialMessage.message
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val response = result.response.contentAsString
            val issueCredentialMessage = subjectIssueCredentialMessenger.parseMessage(response)
            assertIs<NextMessageFinished>(issueCredentialMessage)
            val lastMessage = issueCredentialMessage.lastMessage
            assertIs<IssueCredential>(lastMessage)

            val issuerJwsVc = lastMessage.attachments!!.map { it.data }.mapNotNull { it.jws }
            assertTrue(issuerJwsVc.isNotEmpty())

            subject.storeCredentials(issuerJwsVc)
        }
    }

    @Test
    fun check_revocation() {
        val result = mockMvc.get("/credentials/status/1")
            .andExpect {
                status { isOk() }
            }.andReturn()
        val response = result.response.contentAsString

        println(response)
    }

}