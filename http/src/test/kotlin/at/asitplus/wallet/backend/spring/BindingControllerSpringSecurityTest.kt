package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.TestTimeSource
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.backend.config.KeyType
import at.asitplus.wallet.backend.pki.PkiService
import at.asitplus.wallet.backend.pki.SignedCertificate
import at.asitplus.wallet.backend.service.BindingCertificate
import at.asitplus.wallet.backend.service.BindingParams
import at.asitplus.wallet.backend.service.BindingService
import at.asitplus.wallet.backend.service.ChallengeService
import at.asitplus.wallet.lib.decodeBase64ToArray
import at.asitplus.wallet.lib.jws.JwkType
import at.asitplus.wallet.pupilid.BindingConfirmRequestJ
import at.asitplus.wallet.pupilid.BindingCsrRequestJ
import at.asitplus.wallet.pupilid.BindingParamsRequestJ
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Tests the Spring Security parts of the authentication for [BindingController],
 * i.e. it tests the filter, authentication provider, token and so on.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class BindingControllerSpringSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @MockBean
    private lateinit var extNonceAuthnService: ExtNonceAuthnService

    @MockBean
    private lateinit var challengeService: ChallengeService

    @MockBean
    private lateinit var bindingService: BindingService

    private lateinit var bpk: String
    private lateinit var challenge: ByteArray
    private lateinit var nonce: String
    private lateinit var csr: ByteArray
    private lateinit var deviceName: String
    private lateinit var certificate: ByteArray
    private lateinit var startRequest: BindingParamsRequestJ

    @BeforeEach
    fun beforeEach() {
        bpk = UUID.randomUUID().toString()
        challenge = Random.nextBytes(32)
        csr = Random.nextBytes(32)
        certificate = Random.nextBytes(32)
        whenever(challengeService.generate()).thenReturn(challenge)
        whenever(challengeService.verifyAndRemove(eq(challenge))).thenReturn(true)
        nonce = UUID.randomUUID().toString()
        whenever(extNonceAuthnService.exchangeNonceForBpk(eq(nonce))).thenReturn(bpk)
        deviceName = UUID.randomUUID().toString()
        whenever(bindingService.getBindingParams(eq(deviceName)))
            .thenReturn(BindingParams(challenge, "", JwkType.EC.text))
        whenever(bindingService.signCertificate(eq(csr), eq(challenge), eq(deviceName), any(), eq(bpk)))
            .thenReturn(BindingCertificate(certificate, null))
        whenever(bindingService.confirm(eq(true))).thenReturn(true)
        startRequest = BindingParamsRequestJ(deviceName)
    }

    @Test
    fun start_noAuthn_forbidden() {
        mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(startRequest)
        }.andExpect {
            status { isForbidden() }
            header { doesNotExist(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()
    }

    @Test
    @WithMockUser(authorities = ["PUPIL"])
    fun start_withMockUser_ok() {
        mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(startRequest)
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

    @Test
    fun start_nonce_ok() {
        mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(startRequest)
            header(X_AUTH_EXT_NONCE, nonce)
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

    @Test
    fun start_nonceNotKnown_forbidden() {
        whenever(extNonceAuthnService.exchangeNonceForBpk(eq(nonce))).thenReturn(null)

        mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(startRequest)
            header(X_AUTH_EXT_NONCE, nonce)
        }.andExpect {
            status { isForbidden() }
            header { doesNotExist(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()
    }

    @Test
    fun start_create_confirm_ok() {
        val startResponse = mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(startRequest)
            header(X_AUTH_EXT_NONCE, nonce)
        }.andExpect {
            status { isOk() }
            header { exists(X_AUTH_TOKEN) }
        }.andReturn()

        val xAuthToken = startResponse.response.getHeaderValue(X_AUTH_TOKEN)!!
        val csrRequest = BindingCsrRequestJ(challenge, csr, deviceName, listOf())
        mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(csrRequest)
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val confirmRequest = BindingConfirmRequestJ(true)
        mockMvc.post("/binding/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(confirmRequest)
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        verify(extNonceAuthnService).invalidateNonce(eq(nonce))
    }

    @Test
    fun start_create_noSession() {
        mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(startRequest)
            header(X_AUTH_EXT_NONCE, nonce)
        }.andExpect {
            status { isOk() }
            header { exists(X_AUTH_TOKEN) }
        }.andReturn()

        val csrRequest = BindingCsrRequestJ(challenge, csr, deviceName, listOf())
        mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(csrRequest)
        }.andExpect {
            status { isForbidden() }
            header { doesNotExist(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()
    }

    @Test
    fun start_create_invalidChallenge() {
        val startResponse = mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(startRequest)
            header(X_AUTH_EXT_NONCE, nonce)
        }.andExpect {
            status { isOk() }
            header { exists(X_AUTH_TOKEN) }
        }.andReturn()

        val xAuthToken = startResponse.response.getHeaderValue(X_AUTH_TOKEN)!!
        val csrRequest = BindingCsrRequestJ(Random.nextBytes(32), csr, deviceName, listOf())
        mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(csrRequest)
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()
    }

    @Test
    fun start_create_confirm_create_sessionInvalid() {
        val startResponse = mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(startRequest)
            header(X_AUTH_EXT_NONCE, nonce)
        }.andExpect {
            status { isOk() }
            header { exists(X_AUTH_TOKEN) }
        }.andReturn()

        val xAuthToken = startResponse.response.getHeaderValue(X_AUTH_TOKEN)!!
        val csrRequest = BindingCsrRequestJ(challenge, csr, deviceName, listOf())

        mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(csrRequest)
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val confirmRequest = BindingConfirmRequestJ(true)
        mockMvc.post("/binding/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(confirmRequest)
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isOk() }
            header { doesNotExist(X_AUTH_TOKEN) }
        }.andReturn()

        verify(extNonceAuthnService).invalidateNonce(eq(nonce))

        mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(csrRequest)
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isForbidden() }
            header { doesNotExist(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()
    }

    @Test
    fun start_create_confirm_confirm_sessionInvalid() {
        val startResponse = mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(startRequest)
            header(X_AUTH_EXT_NONCE, nonce)
        }.andExpect {
            status { isOk() }
            header { exists(X_AUTH_TOKEN) }
        }.andReturn()

        val xAuthToken = startResponse.response.getHeaderValue(X_AUTH_TOKEN)!!
        val csrRequest = BindingCsrRequestJ(challenge, csr, deviceName, listOf())

        mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(csrRequest)
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val confirmRequest = BindingConfirmRequestJ(true)
        mockMvc.post("/binding/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(confirmRequest)
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isOk() }
            header { doesNotExist(X_AUTH_TOKEN) }
        }.andReturn()

        verify(extNonceAuthnService).invalidateNonce(eq(nonce))

        mockMvc.post("/binding/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(confirmRequest)
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isForbidden() }
            header { doesNotExist(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()
    }

    companion object {
        private const val X_AUTH_TOKEN = "X-Auth-Token"
        private const val X_AUTH_EXT_NONCE = "X-Auth-ExtNonce"
    }
}


@ActiveProfiles("pupilid")
class PupilIdBindingControllerSpringSecurityTest : BindingControllerSpringSecurityTest() {}


@ActiveProfiles("eidasid")
class EidasIdBindingControllerSpringSecurityTest : BindingControllerSpringSecurityTest() {}
