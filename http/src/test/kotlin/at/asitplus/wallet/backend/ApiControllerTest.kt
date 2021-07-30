package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.Agent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class ApiControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var authTokenService: AuthTokenService

    private lateinit var subject: Agent

    @BeforeEach
    fun beforeEach() {
        subject = Agent()
    }

    @Test
    fun issue_missingToken_400() {
        mockMvc.get("/issue?keyId={keyId}", subject.keyId)
            .andExpect {
                status { is4xxClientError() }
            }.andReturn()
    }

    @Test
    fun issue_wrongToken_401() {
        mockMvc.get("/issue?keyId={keyId}&token={token}", subject.keyId, "foo")
            .andExpect {
                status { isUnauthorized() }
            }.andReturn()
    }

    @Test
    fun issueCredential() {
        val token = authTokenService.generateAuthToken()

        val result = mockMvc.get("/issue?keyId={keyId}&token={token}", subject.keyId, token)
            .andExpect {
                status { isOk() }
            }.andReturn()
        val response = result.response.contentAsString

        subject.storeCredential(response)
    }


    @Test
    fun check_revocation() {
        val result = mockMvc.get("/check?keyId={keyId}", subject.keyId)
            .andExpect {
                status { isOk() }
            }.andReturn()
        val response = result.response.contentAsString
    }

}