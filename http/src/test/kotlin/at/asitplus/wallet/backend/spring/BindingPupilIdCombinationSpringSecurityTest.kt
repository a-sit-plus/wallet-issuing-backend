package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.ChallengeService
import at.asitplus.wallet.backend.DeviceBindingAuthnResult
import at.asitplus.wallet.backend.DeviceBindingAuthnService
import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.IssueCredentialAdapter
import at.asitplus.wallet.backend.PkiService
import at.asitplus.wallet.backend.SignedCertificate
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.lib.agent.NextMessage
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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/**
 * Tests the Spring Security parts of the authentication for [DeviceBinding] and [PupilIdController]
 * used in succession, i.e. to get a device binding and pupilid in one session for clients.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BindingPupilIdCombinationSpringSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @MockBean
    private lateinit var extNonceAuthnService: ExtNonceAuthnService

    @MockBean
    private lateinit var pkiService: PkiService

    @MockBean
    private lateinit var challengeService: ChallengeService

    @MockBean
    private lateinit var issueCredentialAdapter: IssueCredentialAdapter

    @MockBean
    private lateinit var deviceBindingStorageService: DeviceBindingStorageService

    @MockBean
    private lateinit var deviceBindingAuthnService: DeviceBindingAuthnService

    private lateinit var bpk: String
    private lateinit var challenge: ByteArray
    private lateinit var nonce: String
    private lateinit var csr: ByteArray
    private lateinit var deviceName: String
    private lateinit var deviceId: String
    private lateinit var certificate: ByteArray
    private lateinit var startRequest: BindingParamsRequestJ
    private lateinit var clientMessage: String
    private lateinit var serverMessage: String
    private lateinit var challengeResponse: String

    @BeforeEach
    fun beforeEach() {
        bpk = UUID.randomUUID().toString()
        challenge = Random.nextBytes(32)
        nonce = UUID.randomUUID().toString()
        csr = Random.nextBytes(32)
        deviceName = UUID.randomUUID().toString()
        deviceId = UUID.randomUUID().toString()
        certificate = Random.nextBytes(32)
        startRequest = BindingParamsRequestJ(UUID.randomUUID().toString())
        clientMessage = UUID.randomUUID().toString()
        serverMessage = UUID.randomUUID().toString()
        challengeResponse = UUID.randomUUID().toString()
        val validUntil = Instant.now().plusSeconds(60)

        whenever(challengeService.generate()).thenReturn(challenge)
        whenever(challengeService.verifyAndRemove(eq(challenge))).thenReturn(true)
        whenever(extNonceAuthnService.exchangeNonceForBpk(eq(nonce))).thenReturn(bpk)
        val signedCertificate = SignedCertificate(certificate, validUntil)
        whenever(pkiService.verifyAndSign(eq(csr), any())).thenReturn(signedCertificate)
        whenever(issueCredentialAdapter.parseMessage(eq(clientMessage)))
            .thenReturn(NextMessage.Send(serverMessage, null))
        whenever(deviceBindingAuthnService.validate(eq(challengeResponse)))
            .thenReturn(DeviceBindingAuthnResult(bpk, certificate))
        val deviceBinding = DeviceBinding(bpk, certificate, deviceName, deviceId, validUntil)
        whenever(deviceBindingStorageService.getDeviceBindingForCurrentUser())
            .thenReturn(deviceBinding)
    }

    @Test
    fun start_create_confirm_issue_ok() {
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

        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = clientMessage
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

    companion object {
        private const val X_AUTH_TOKEN = "X-Auth-Token"
        private const val X_AUTH_EXT_NONCE = "X-Auth-ExtNonce"
    }

}