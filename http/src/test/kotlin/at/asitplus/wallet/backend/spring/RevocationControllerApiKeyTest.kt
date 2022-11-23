package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.auth.WebSecurityConstants.X_API_KEY
import at.asitplus.wallet.backend.service.DeviceBindingStorageService
import at.asitplus.wallet.backend.controller.RevocationController
import at.asitplus.wallet.backend.service.RevocationService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders.WWW_AUTHENTICATE
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * Tests the Spring Security parts of the authentication for [RevocationController],
 * i.e. it tests the filter, authentication provider, token and so on.
 */
@SpringBootTest
@ActiveProfiles(profiles = ["pupilid", "apikey"])
@AutoConfigureMockMvc
class RevocationControllerApiKeyTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @MockBean
    private lateinit var bindingStorageService: DeviceBindingStorageService

    @MockBean
    private lateinit var revocationService: RevocationService

    // from application-apikey.yml
    private val apiKey: String = "2zo7fr3hft3f0zg758d5"
    private lateinit var bpk: String
    private lateinit var deviceId: String
    private lateinit var request: RevocationController.RevocationRequest

    @BeforeEach
    fun beforeEach() {
        bpk = UUID.randomUUID().toString()
        deviceId = UUID.randomUUID().toString()
        whenever(revocationService.revokeBinding(eq(bpk), eq(deviceId)))
            .thenReturn(1)
        whenever(revocationService.revokeCredentialsByBpkAndDeviceId(eq(bpk), eq(deviceId)))
            .thenReturn(1)
        request = RevocationController.RevocationRequest(bpk = bpk, deviceId = deviceId)
    }

    @Test
    fun start_noAuthn_forbidden() {
        mockMvc.post("/revoke/binding") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isForbidden() }
            header { doesNotExist(WWW_AUTHENTICATE) }
        }.andReturn()
    }

    @Test
    fun start_apiKey_ok() {
        mockMvc.post("/revoke/binding") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
            header(X_API_KEY, apiKey)
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

}