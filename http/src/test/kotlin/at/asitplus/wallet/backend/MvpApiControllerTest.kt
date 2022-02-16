package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.IssueCredentialProtocolResult
import at.asitplus.wallet.lib.agent.MessageWrapper
import at.asitplus.wallet.lib.agent.NextMessage
import kotlinx.coroutines.test.runTest
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
class MvpApiControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var issueCredentialMessengerPupilId: IssueCredentialMessenger

    @Autowired
    private lateinit var issueCredentialMessengerGreenPass: IssueCredentialMessenger

    private lateinit var subject: Agent

    private lateinit var subjectIssueCredentialMessenger: IssueCredentialMessenger

    @BeforeEach
    fun beforeEach() {
        subject = Agent()
        subjectIssueCredentialMessenger = IssueCredentialMessenger(
            subject,
            MessageWrapper(subject.cryptoService),
            "https://example.com/issue"
        )
    }

    @Test
    fun issue_wrongMessage_400() = runTest {
        val requestCredentialMessage = subjectIssueCredentialMessenger.startCreatingInvitation()
        assertIs<NextMessage.Send>(requestCredentialMessage)

        mockMvc.post("/issue") {
            content = requestCredentialMessage.message
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()
    }

    @Test
    fun issue_wrongInvitation_400() = runTest {
        val agent = Agent()
        val oobInvitation = IssueCredentialMessenger(
            Agent(),
            MessageWrapper(agent.cryptoService),
            "https://example.com/issue"
        ).startCreatingInvitation()
        assertIs<NextMessage.Send>(oobInvitation)

        val requestCredentialMessage = subjectIssueCredentialMessenger.parseMessage(oobInvitation.message)
        assertIs<NextMessage.Send>(requestCredentialMessage)

        mockMvc.post("/issue") {
            content = requestCredentialMessage.message
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()
    }

    @Test
    fun issue_success_pupilid() = runTest {
        val oobInvitation = issueCredentialMessengerPupilId.startCreatingInvitation()
        assertIs<NextMessage.Send>(oobInvitation)

        simulateWallet(oobInvitation)
    }

    @Test
    fun issue_success_greenPass() = runTest {
        val oobInvitation = issueCredentialMessengerGreenPass.startCreatingInvitation()
        assertIs<NextMessage.Send>(oobInvitation)

        simulateWallet(oobInvitation)
    }

    private suspend fun simulateWallet(oobInvitation: NextMessage.Send) {
        val requestCredentialMessage = subjectIssueCredentialMessenger.parseMessage(oobInvitation.message)
        assertIs<NextMessage.Send>(requestCredentialMessage)

        val result = mockMvc.post("/issue") {
            content = requestCredentialMessage.message
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val response = result.response.contentAsString
        val issueCredentialMessage = subjectIssueCredentialMessenger.parseMessage(response)
        assertIs<NextMessage.Result<IssueCredentialProtocolResult>>(issueCredentialMessage)
        val issuerJwsVc = issueCredentialMessage.result
        assertTrue(issuerJwsVc.accepted.isNotEmpty())
    }

    @Test
    fun check_revocation() {
        mockMvc.get("/credentials/status/1")
            .andExpect {
                status { isOk() }
            }.andReturn()
    }

}