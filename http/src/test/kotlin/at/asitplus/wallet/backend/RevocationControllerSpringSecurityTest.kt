package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.ApiKeyAuthnService
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders.WWW_AUTHENTICATE
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * Tests the Spring Security parts of the authentication for [RevocationController],
 * i.e. it tests the filter, authentication provider, token and so on.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.LOG_DEBUG)
class RevocationControllerSpringSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @MockBean
    private lateinit var apiKeyAuthnService: ApiKeyAuthnService

    @MockBean
    private lateinit var revocationService: RevocationService

    private lateinit var apiKey: String
    private lateinit var bpk: String
    private lateinit var deviceId: String
    private lateinit var request: RevocationController.RevocationRequest

    @BeforeEach
    fun beforeEach() {
        apiKey = UUID.randomUUID().toString()
        bpk = UUID.randomUUID().toString()
        deviceId = UUID.randomUUID().toString()
        whenever(apiKeyAuthnService.validate(eq(apiKey)))
            .thenReturn("user")
        whenever(revocationService.revokeCredentialsByBpkAndDeviceId(eq(bpk), eq(deviceId)))
            .thenReturn(1)
        request = RevocationController.RevocationRequest(bpk = bpk, deviceId = deviceId)
    }

    @Test
    fun start_noAuthn_forbidden() = runTest {
        mockMvc.post("/revoke/binding") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isForbidden() }
            header { doesNotExist(WWW_AUTHENTICATE) }
        }.andReturn()
    }

    @Test
    @WithMockUser(authorities = ["REVOCATION"])
    fun start_withMockUser_ok() = runTest {
        mockMvc.post("/revoke/binding") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

    @Test
    fun start_nonce_ok() = runTest {
        mockMvc.post("/revoke/binding") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
            header(X_API_KEY, apiKey)
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

    @Test
    fun start_nonceNotKnown_forbidden() = runTest {
        whenever(apiKeyAuthnService.validate(eq(apiKey))).thenReturn(null)

        mockMvc.post("/revoke/binding") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
            header(X_API_KEY, apiKey)
        }.andExpect {
            status { isForbidden() }
            header { doesNotExist(WWW_AUTHENTICATE) }
        }.andReturn()
    }

    companion object {
        private const val X_API_KEY = "X-API-Key"
    }
}