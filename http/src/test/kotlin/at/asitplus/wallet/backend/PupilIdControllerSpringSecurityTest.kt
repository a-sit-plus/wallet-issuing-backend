package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.PupilIdControllerSpringSecurityTest.UserDetailsServiceInt.certificate
import at.asitplus.wallet.backend.auth.AuthenticatedDeviceBindingUser
import at.asitplus.wallet.lib.agent.NextMessage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.test.context.support.WithUserDetails
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.random.Random

/**
 * Tests the Spring Security parts of the authentication for [PupilIdController],
 * i.e. it tests the filter, authentication provider, token and so on.
 */
@SpringBootTest(classes = [PupilIdControllerSpringSecurityTest.TestConfig::class])
@AutoConfigureMockMvc(print = MockMvcPrint.LOG_DEBUG)
class PupilIdControllerSpringSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var pupilIdService: PupilIdService

    @MockBean
    private lateinit var deviceBindingAuthnService: DeviceBindingAuthnService

    private lateinit var clientMessage: String
    private lateinit var serverMessage: String
    private lateinit var certificate: ByteArray
    private lateinit var bpk: String
    private lateinit var challengeResponse: String

    /**
     * Workaround to be able to read the random [certificate] (for other mock beans),
     * that will be used in the user details of the authenticated user.
     */
    private object UserDetailsServiceInt : UserDetailsService {

        val bpk = UUID.randomUUID().toString()
        val certificate: ByteArray = Random.nextBytes(32)

        override fun loadUserByUsername(username: String): UserDetails {
            return AuthenticatedDeviceBindingUser(bpk, certificate)
        }

    }

    /**
     * Class needed to define a bean called [userDetailsServiceInt] that
     * can be picked up by the [WithUserDetails] annotation in a test case
     */
    @TestConfiguration
    internal class TestConfig {
        @Bean
        fun userDetailsServiceInt(): UserDetailsService {
            return UserDetailsServiceInt
        }
    }

    @BeforeEach
    fun beforeEach() {
        clientMessage = UUID.randomUUID().toString()
        serverMessage = UUID.randomUUID().toString()
        bpk = UserDetailsServiceInt.bpk
        certificate = UserDetailsServiceInt.certificate
        challengeResponse = UUID.randomUUID().toString()
        whenever(pupilIdService.parseMessage(eq(clientMessage), eq(bpk), eq(certificate)))
            .thenReturn(NextMessage.Send(serverMessage, null))
        whenever(deviceBindingAuthnService.validate(eq(challengeResponse)))
            .thenReturn(DeviceBindingAuthnResult(bpk, certificate))
    }

    @Test
    fun start_noAuthn_forbidden() = runTest {
        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = "does not matter"
        }.andExpect {
            status { isUnauthorized() }
        }.andReturn()
    }

    @Test
    @WithUserDetails(userDetailsServiceBeanName = "userDetailsServiceInt")
    fun start_withMockUser_ok() = runTest {
        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

    @Test
    fun start_challengeResponse_ok() = runTest {
        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
        }.andExpect {
            status { isUnauthorized() }
            header { exists(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()

        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
            header(HttpHeaders.AUTHORIZATION, "Response $challengeResponse")
        }.andExpect {
            status { isOk() }
            header { exists(X_AUTH_TOKEN) }
        }.andReturn()
    }

    @Test
    fun start_challengeResponse_sessionInvalid() = runTest {
        val firstResponse = mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
        }.andExpect {
            status { isUnauthorized() }
            header { exists(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()

        val xAuthToken = firstResponse.response.getHeaderValue(X_AUTH_TOKEN)!!

        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
            header(HttpHeaders.AUTHORIZATION, "Response $challengeResponse")
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isOk() }
            header { doesNotExist(X_AUTH_TOKEN) }
        }.andReturn()

        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isUnauthorized() }
        }.andReturn()
    }

    @Test
    fun start_challengeResponse_invalid() = runTest {
        whenever(deviceBindingAuthnService.validate(eq(challengeResponse)))
            .thenReturn(null)

        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
        }.andExpect {
            status { isUnauthorized() }
            header { exists(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()

        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
            header(HttpHeaders.AUTHORIZATION, "Response $challengeResponse")
        }.andExpect {
            status { isUnauthorized() }
        }.andReturn()
    }

    companion object {
        private const val X_AUTH_TOKEN = "X-Auth-Token"
    }

}