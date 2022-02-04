package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.encodeBase16
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.random.Random

@SpringBootTest
@AutoConfigureMockMvc
class BindingControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @Test
    fun start_noAuthn_forbidden() = runTest {
        val request = BindingController.BindingParamsRequest("unit test")

        mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isForbidden() }
        }.andReturn()
    }

    @Test
    @WithMockUser(authorities = ["PUPIL"])
    fun start_withMockUser_ok() = runTest {
        val request = BindingController.BindingParamsRequest("unit test")

        mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

    @Test
    fun start_nonce_ok() = runTest {
        val request = BindingController.BindingParamsRequest("unit test")
        val nonce = Random.Default.nextBytes(32).encodeBase16()

        mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
            header("Authorization", "Nonce $nonce")
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

    @Test
    fun start_create_ok() = runTest {
        val requestStart = BindingController.BindingParamsRequest("unit test")
        val nonce = Random.Default.nextBytes(32).encodeBase16()

        val startResponse = mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(requestStart)
            header("Authorization", "Nonce $nonce")
        }.andExpect {
            status { isOk() }
        }.andReturn()
        val xAuthToken = startResponse.response.getHeaderValue("X-Auth-Token")!!
        val challenge =
            mapper.readValue<BindingController.BindingParamsResponse>(startResponse.response.contentAsString).challenge

        val requestCsr = BindingController.BindingCsrRequest(challenge, Random.nextBytes(32))
        mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(requestCsr)
            header("X-Auth-Token", xAuthToken)
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

    @Test
    fun start_create_create_sessionInvalid() = runTest {
        val requestStart = BindingController.BindingParamsRequest("unit test")
        val nonce = Random.Default.nextBytes(32).encodeBase16()

        val startResponse = mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(requestStart)
            header("Authorization", "Nonce $nonce")
        }.andExpect {
            status { isOk() }
        }.andReturn()
        val xAuthToken = startResponse.response.getHeaderValue("X-Auth-Token")!!
        val challenge =
            mapper.readValue<BindingController.BindingParamsResponse>(startResponse.response.contentAsString).challenge

        val requestCsr = BindingController.BindingCsrRequest(challenge, Random.nextBytes(32))
        mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(requestCsr)
            header("X-Auth-Token", xAuthToken)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(requestCsr)
            header("X-Auth-Token", xAuthToken)
        }.andExpect {
            status { isForbidden() }
        }.andReturn()
    }

}