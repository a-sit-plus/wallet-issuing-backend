package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.pupilid.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.security.cert.CertificateFactory
import java.util.*

/**
 * Simulates a full run of a client for using the [BindingController].
 */
@SpringBootTest
@AutoConfigureMockMvc
class BindingControllerFullRunTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var extNonceAuthnService: ExtNonceAuthnService

    @Autowired
    private lateinit var mapper: ObjectMapper

    private lateinit var nonce: String
    private lateinit var bpk: String
    private lateinit var deviceName: String
    private lateinit var client: Client

    @BeforeEach
    fun beforeEach() {
        nonce = UUID.randomUUID().toString()
        bpk = UUID.randomUUID().toString()
        deviceName = UUID.randomUUID().toString()
        whenever(extNonceAuthnService.exchangeNonceForBpk(eq(nonce))).thenReturn(bpk)
        client = Client()
    }

    @Test
    fun start_create_ok() {
        val startRequest = BindingParamsRequestJ(UUID.randomUUID().toString())

        val startResponse = mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(startRequest)
            header(X_AUTH_EXT_NONCE, nonce)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val bindingParamsResponse = mapper.readValue<BindingParamsResponse>(startResponse.response.contentAsString)
        val challenge = bindingParamsResponse.challenge
        val subject = bindingParamsResponse.subject

        val xAuthToken = startResponse.response.getHeaderValue(X_AUTH_TOKEN)!!
        val csrRequest = BindingCsrRequestJ(challenge, client.generateCsr(subject), deviceName, listOf())

        val createResponse = mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(csrRequest)
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val certBytes = mapper.readValue<BindingCsrResponseJ>(createResponse.response.contentAsString).certificate
        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(certBytes.inputStream())
        client.keyPair.public.encoded shouldBe certificate.publicKey.encoded

        val confirmRequest = BindingConfirmRequestJ(true)

        mockMvc.post("/binding/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(confirmRequest)
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