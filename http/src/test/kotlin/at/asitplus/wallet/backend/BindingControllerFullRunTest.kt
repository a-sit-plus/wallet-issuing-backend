package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.NonceToBpkService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.CertificateFactory
import java.util.UUID
import kotlin.test.assertContentEquals

/**
 * Simulates a full run of a client for using the [BindingController].
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.LOG_DEBUG)
class BindingControllerFullRunTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var nonceToBpkService: NonceToBpkService

    @Autowired
    private lateinit var mapper: ObjectMapper

    private lateinit var nonce: String
    private lateinit var bpk: String
    private lateinit var deviceName: String

    @BeforeEach
    fun beforeEach() {
        nonce = UUID.randomUUID().toString()
        bpk = UUID.randomUUID().toString()
        deviceName = UUID.randomUUID().toString()
        whenever(nonceToBpkService.validate(eq(nonce))).thenReturn(bpk)
    }

    @Test
    fun start_create_ok() = runTest {
        val startRequest = BindingController.BindingParamsRequest(UUID.randomUUID().toString())
        val keyPair = KeyPairGenerator.getInstance("EC").generateKeyPair()!!

        val startResponse = mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(startRequest)
            header(HttpHeaders.AUTHORIZATION, "Nonce $nonce")
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val challenge =
            mapper.readValue<BindingController.BindingParamsResponse>(startResponse.response.contentAsString).challenge

        val xAuthToken = startResponse.response.getHeaderValue(X_AUTH_TOKEN)!!
        val csrRequest = BindingController.BindingCsrRequest(challenge, generateCsr(keyPair), deviceName)

        val createResponse = mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(csrRequest)
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val certBytes =
            mapper.readValue<BindingController.BindingCsrResponse>(createResponse.response.contentAsString).certificate
        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(certBytes.inputStream())
        assertContentEquals(keyPair.public.encoded, certificate.publicKey.encoded)
    }

    private fun generateCsr(keyPair: KeyPair): ByteArray {
        return JcaPKCS10CertificationRequestBuilder(X500Name("CN=Subject"), keyPair.public).build(
            JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
        ).encoded
    }

    companion object {
        private const val X_AUTH_TOKEN = "X-Auth-Token"
    }
}