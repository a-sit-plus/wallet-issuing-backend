package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.*
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.pki.PkiService
import at.asitplus.wallet.backend.pki.SignedCertificate
import at.asitplus.wallet.backend.service.ChallengeService
import at.asitplus.wallet.backend.service.IssueCredentialAdapter
import at.asitplus.wallet.lib.agent.NextMessage
import at.asitplus.wallet.lib.decodeBase64ToArray
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
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

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
    private lateinit var authenticationSupplier: AuthenticationSupplier

    @MockBean
    private lateinit var deviceBindingAuthnService: DeviceBindingAuthnService

    private lateinit var bpk: String
    private lateinit var challenge: ByteArray
    private lateinit var nonce: String
    private val csr = """
        MIHGMG4CAQAwDDEKMAgGA1UEAwwBLjBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABAoTFPKbA4/4
        VE6c+HPVU/KK8pW2c8oXeEUzxoylFJyCmGf2x7ZiVrSs1140AJvbAqDEiiziOE8ngMMuDf6y9P2g
        ADAKBggqhkjOPQQDAgNIADBFAiAN/VZeV9lUYWmjnAZstsopfWbdA/L2FP84SAX1X9mJrwIhAN3Z
        vFQDG6sjaZ8aho16EdlV53Q4FQIlwUIYcGjAbZbj"""
        .trimMargin().decodeBase64ToArray()!!

    private lateinit var deviceName: String
    private val certificate: ByteArray = (CertificateFactory.getInstance("X.509")
        .generateCertificate(
            """
            MIICujCCAmCgAwIBAgIBATAKBggqhkjOPQQDAjA5MQwwCgYDVQQMDANURUUxKTAnBgNVBAUTIDg3ZWVkZjAzYjljZWNlMjIwYzgzMTJhMmI5ZDZiMjZlMB4XDTIy
            MDkyNzExMzY0OVoXDTQ4MDEwMTAwMDAwMFowFTETMBEGA1UEAxMKYmluZGluZ0tleTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABItRPUbUcbA7u0reFB0FHMvDlN/Oc/Ez1DlFsaNX/QHHGr5sgX4rJj3g6BqvwDxFfdjR8VnrB2wVqnAd1vCMNnejggF7MIIBdzAOBgNVHQ8BAf8EBAMCB4AwggFjBgorBgEEAdZ5AgERBIIBUzCCAU8CAgDICgEBAgIAyAoBAQQgg
            NbnnEX4D8L1U7rt6XyzQNZ3WlN/e/H9wDjd/qXvHpsEADBlv4U9CAIGAYN+vD4Xv4VFVQRTMFExKzApBCRhdC5hc2l0cGx1cy5kaWdpdGFsaWQud2FsbGV0LnB1cGlsaWQCAQExIgQg5UGooDS29UheXLyz12rlTbB36v/396mnrpycpGx0qlIwgbOhCDEGAgECAgEDogMCAQOjBAICAQClCzEJAgEAAgECAgEEqgMCAQG/g3gDAgEDv4N5BAICASy/hT4DAgEAv4VATD
            BKBCAPbnXIAYO13sB0sAVNQnHpk4nr5LE2sIGd4fFQug/51wEB/woBAAQgXOGC1inSwG9tTnpx1AflWgKWqTRrXBhScS9NTK0DMlS/hUEFAgMB+9C/hUIFAgMDFeG/hU4GAgQBNIvpv4VPBgIEATSL6TAKBggqhkjOPQQDAgNIADBFAiEA493XrIO83zpV6iMnPvLb9yzyZcp0nRS8PZIvAOdnkBYCIFM4RykcJJ8U984j03Wyb554OWJpBvDenwKKG4MAN/LH
            """.trimMargin().decodeBase64ToArray()!!.inputStream()
        ) as X509Certificate).encoded
    private lateinit var startRequest: BindingParamsRequestJ
    private lateinit var clientMessage: String
    private lateinit var serverMessage: String
    private lateinit var challengeResponse: String

    @BeforeEach
    fun beforeEach() {
        bpk = UUID.randomUUID().toString()
        challenge = Random.nextBytes(32)
        nonce = UUID.randomUUID().toString()
        deviceName = UUID.randomUUID().toString()
        startRequest = BindingParamsRequestJ(UUID.randomUUID().toString())
        clientMessage = UUID.randomUUID().toString()
        serverMessage = UUID.randomUUID().toString()
        challengeResponse = UUID.randomUUID().toString()
        val validUntil = TestTimeSource.now() + 60.seconds

        whenever(challengeService.generate()).thenReturn(challenge)
        whenever(challengeService.verifyAndRemove(eq(challenge))).thenReturn(true)
        whenever(extNonceAuthnService.exchangeNonceForBpk(eq(nonce))).thenReturn(bpk)
        val signedCertificate = SignedCertificate(certificate, validUntil)
        whenever(pkiService.verifyAndSign(eq(csr), any())).thenReturn(signedCertificate)
        whenever(issueCredentialAdapter.parseMessage(eq(clientMessage)))
            .thenReturn(NextMessage.Send(serverMessage, null))
        whenever(deviceBindingAuthnService.validate(eq(challengeResponse)))
            .thenReturn(DeviceBindingAuthnResult(bpk, certificate))
        whenever(authenticationSupplier.getCurrentUserCertificate())
            .thenReturn(certificate)
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