package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.data.Agent
import at.asitplus.wallet.backend.data.VerifiableCredentialSerialized
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

    @Test
    fun issueCredential() {
        val subject = Agent()

        val result = mockMvc.get("/issue?keyId={}", subject.keyId)
            .andExpect {
                status { isOk() }
            }.andReturn()
        val response = result.response.contentAsString

        subject.storeCredential(VerifiableCredentialSerialized(response))
    }

}