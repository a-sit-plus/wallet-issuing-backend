package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.DeviceListEntry
import at.asitplus.wallet.backend.PkiService
import at.asitplus.wallet.backend.RevocationController
import at.asitplus.wallet.backend.RevocationService
import at.asitplus.wallet.backend.data.DeviceBinding
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
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
@WithMockUser(authorities = ["REVOCATION"])
class RevocationControllerLogicTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @MockBean
    private lateinit var bindingStorageService: DeviceBindingStorageService

    @MockBean
    private lateinit var revocationService: RevocationService

    @MockBean
    private lateinit var pkiService: PkiService

    private lateinit var bpk: String
    private lateinit var deviceName: String
    private lateinit var deviceId: String
    private lateinit var certificate: ByteArray

    @BeforeEach
    fun beforeEach() {
        bpk = UUID.randomUUID().toString()
        deviceName = UUID.randomUUID().toString()
        deviceId = UUID.randomUUID().toString()
        certificate = Client().selfSignedCert.encoded
        whenever(bindingStorageService.lookupDevices(eq(bpk)))
            .thenReturn(listOf(DeviceListEntry(deviceName, deviceId)))
        whenever(bindingStorageService.revoke(eq(bpk), eq(deviceId)))
            .thenReturn(listOf(DeviceBinding(bpk, certificate, deviceName, deviceId)))
        whenever(bindingStorageService.revoke(eq(bpk), eq(null)))
            .thenReturn(listOf(DeviceBinding(bpk, certificate, deviceName, deviceId)))
        whenever(revocationService.revokeCredentialsByBpkAndDeviceId(eq(bpk), eq(deviceId)))
            .thenReturn(1)
        whenever(revocationService.revokeCredentialsByBpkAndDeviceId(eq(bpk), eq(null)))
            .thenReturn(1)
        whenever(revocationService.revokeCredentialsByBpk(eq(bpk)))
            .thenReturn(1)
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
        val request = RevocationController.RevocationRequest(bpk)
        val expectedResponse = RevocationController.RevocationResponse(1)

        mockMvc.post("/revoke/binding") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { json(mapper.writeValueAsString(expectedResponse)) }
        }.andReturn()

        verify(pkiService).revokeCertificate(eq(certificate))
    }

    @Test
    fun binding_bpkAndDeviceId_200() = runTest {
        val request = RevocationController.RevocationRequest(bpk, deviceId)
        val expectedResponse = RevocationController.RevocationResponse(1)

        mockMvc.post("/revoke/binding") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { json(mapper.writeValueAsString(expectedResponse)) }
        }.andReturn()

        verify(pkiService).revokeCertificate(eq(certificate))
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
        val request = RevocationController.RevocationRequest(bpk)
        val expectedResponse = RevocationController.RevocationResponse(1)

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
        val request = RevocationController.RevocationRequest(bpk, deviceId)

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