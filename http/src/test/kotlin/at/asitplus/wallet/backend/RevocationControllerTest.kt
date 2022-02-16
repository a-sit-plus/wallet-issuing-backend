package at.asitplus.wallet.backend

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class RevocationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @MockBean
    private lateinit var bindingStorageService: DeviceBindingStorageService

    private lateinit var bpk: String
    private lateinit var deviceName: String
    private lateinit var deviceId: String

    @BeforeEach
    fun beforeEach() {
        bpk = UUID.randomUUID().toString()
        deviceName = UUID.randomUUID().toString()
        deviceId = UUID.randomUUID().toString()
        whenever(bindingStorageService.lookupDevices(eq(bpk)))
            .thenReturn(listOf(DeviceListEntry(deviceName, deviceId)))
    }

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
    fun devices_noParam_400() = runTest {
        mockMvc.get("/revoke/devices") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()
    }

    @Test
    fun devices_bpk_200() = runTest {
        val expectedResponse = RevocationController.DeviceListResponse(
            listOf(RevocationController.DeviceListResponseEntry(deviceId, deviceName))
        )

        mockMvc.get("/revoke/devices?bpk=$bpk") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            content { json(mapper.writeValueAsString(expectedResponse)) }
        }.andReturn()
    }

    @Test
    fun devices_bpk_404() = runTest {
        mockMvc.get("/revoke/devices?bpk=foo") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
        }.andReturn()
    }

    @Test
    fun devices_bpkEncoded_404() = runTest {
        mockMvc.get("/revoke/devices?bpk=%3D%2B%2F") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
        }.andReturn()
    }

}