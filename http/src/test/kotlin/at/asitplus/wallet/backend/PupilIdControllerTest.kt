package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.encodeBase64
import at.asitplus.wallet.lib.msg.DummyMessage
import com.fasterxml.jackson.databind.ObjectMapper
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

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.LOG_DEBUG)
class PupilIdControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun start_noAuthn_forbidden() = runTest {
        val request = DummyMessage("foo").serialize()

        mockMvc.post("/pupilid/issue/start") {
            contentType = MediaType.APPLICATION_JSON
            content = request
        }.andExpect {
            status { isUnauthorized() }
        }.andReturn()
    }

    @Test
    @WithMockUser(authorities = ["DEVICE_BINDING"])
    fun start_withMockUser_ok() = runTest {
        val request = DummyMessage("foo").serialize()

        mockMvc.post("/pupilid/issue/start") {
            contentType = MediaType.APPLICATION_JSON
            content = request
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

    @Test
    fun start_challengeResponse_ok() = runTest {
        val request = DummyMessage("foo").serialize()

        val response = mockMvc.post("/pupilid/issue/start") {
            contentType = MediaType.APPLICATION_JSON
            content = request
        }.andExpect {
            status { isUnauthorized() }
            header { exists("WWW-Authenticate") }
        }.andReturn()

        val challenge = response.response.getHeaderValue("WWW-Authenticate").toString().removePrefix("Challenge ")
        val challengeResponse = Random.nextBytes(32).encodeBase64()

        mockMvc.post("/pupilid/issue/start") {
            contentType = MediaType.APPLICATION_JSON
            content = request
            header("Authorization", "Response $challengeResponse")
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

}