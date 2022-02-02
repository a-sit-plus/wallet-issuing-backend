package at.asitplus.wallet.backend

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class RevocationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @Test
    fun binding_wrongMessage_400() = runTest {
        mockMvc.post("/revoke/binding") {
            contentType = MediaType.APPLICATION_JSON
            content = "foo"
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()
    }

    @Test
    fun binding_bpk_200() = runTest {
        val request = RevocationController.RevocationRequest("bpk")
        val expectedResponse = RevocationController.RevocationResponse(true)

        mockMvc.post("/revoke/binding") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { json(mapper.writeValueAsString(expectedResponse)) }
        }.andReturn()
    }

    @Test
    fun binding_bpkAndDeviceId_200() = runTest {
        val request = RevocationController.RevocationRequest("bpk", "deviceId")
        val expectedResponse = RevocationController.RevocationResponse(true)

        mockMvc.post("/revoke/binding") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { json(mapper.writeValueAsString(expectedResponse)) }
        }.andReturn()
    }

    @Test
    fun pupilid_wrongMessage_400() = runTest {
        mockMvc.post("/revoke/pupilid") {
            contentType = MediaType.APPLICATION_JSON
            content = "foo"
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()
    }

    @Test
    fun pupilid_bpk_200() = runTest {
        val request = RevocationController.RevocationRequest("bpk")
        val expectedResponse = RevocationController.RevocationResponse(true)

        mockMvc.post("/revoke/pupilid") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { json(mapper.writeValueAsString(expectedResponse)) }
        }.andReturn()
    }

    @Test
    fun pupilid_bpkAndDeviceId_400() = runTest {
        val request = RevocationController.RevocationRequest("bpk", "deviceId")

        mockMvc.post("/revoke/pupilid") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()
    }

    @Test
    fun devices_wrongMessage_400() = runTest {
        mockMvc.get("/revoke/devices/") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
        }.andReturn()
    }

    @Test
    fun devices_bpk_200() = runTest {
        mockMvc.get("/revoke/devices/foo") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

}