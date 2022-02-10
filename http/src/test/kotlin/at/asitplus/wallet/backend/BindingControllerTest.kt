package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.NonceToBpkService
import at.asitplus.wallet.lib.encodeBase16
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
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
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.security.KeyPair
import java.security.KeyPairGenerator
import kotlin.random.Random

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.LOG_DEBUG)
class BindingControllerTest {

    private val X_AUTH_TOKEN = "X-Auth-Token"

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @MockBean
    private lateinit var nonceToBpkService: NonceToBpkService

    @Test
    fun start_noAuthn_forbidden() = runTest {
        val request = BindingController.BindingParamsRequest("unit test")

        mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isUnauthorized() }
        }.andReturn()
    }

    @Test
    @WithMockUser(authorities = ["PUPIL"])
    fun start_withMockUser_ok() = runTest {
        val request = BindingController.BindingParamsRequest("unit test")

        mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

    @Test
    fun start_nonce_ok() = runTest {
        val request = BindingController.BindingParamsRequest("unit test")
        val nonce = Random.Default.nextBytes(32).encodeBase16()
        whenever(nonceToBpkService.exchangeForBpk(eq(nonce))).thenReturn("bpk")

        mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
            header(HttpHeaders.AUTHORIZATION, "Nonce $nonce")
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

    @Test
    fun start_nonceNotKnown_unauthorized() = runTest {
        val request = BindingController.BindingParamsRequest("unit test")
        val nonce = Random.Default.nextBytes(32).encodeBase16()
        whenever(nonceToBpkService.exchangeForBpk(eq(nonce))).thenReturn(null)

        mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
            header(HttpHeaders.AUTHORIZATION, "Nonce $nonce")
        }.andExpect {
            status { isUnauthorized() }
        }.andReturn()
    }

    @Test
    fun start_create_ok() = runTest {
        val requestStart = BindingController.BindingParamsRequest("unit test")
        val nonce = Random.Default.nextBytes(32).encodeBase16()
        whenever(nonceToBpkService.exchangeForBpk(eq(nonce))).thenReturn("bpk")
        val keyPair = KeyPairGenerator.getInstance("EC").generateKeyPair()!!

        val startResponse = mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(requestStart)
            header(HttpHeaders.AUTHORIZATION, "Nonce $nonce")
        }.andExpect {
            status { isOk() }
        }.andReturn()
        val xAuthToken = startResponse.response.getHeaderValue(X_AUTH_TOKEN)!!
        val challenge =
            mapper.readValue<BindingController.BindingParamsResponse>(startResponse.response.contentAsString).challenge

        val requestCsr = BindingController.BindingCsrRequest(challenge, generateCsr(keyPair))
        mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(requestCsr)
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

    @Test
    fun start_create_invalidChallenge() = runTest {
        val requestStart = BindingController.BindingParamsRequest("unit test")
        val nonce = Random.Default.nextBytes(32).encodeBase16()
        whenever(nonceToBpkService.exchangeForBpk(eq(nonce))).thenReturn("bpk")
        val keyPair = KeyPairGenerator.getInstance("EC").generateKeyPair()!!

        val startResponse = mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(requestStart)
            header(HttpHeaders.AUTHORIZATION, "Nonce $nonce")
        }.andExpect {
            status { isOk() }
        }.andReturn()
        val xAuthToken = startResponse.response.getHeaderValue(X_AUTH_TOKEN)!!

        val requestCsr = BindingController.BindingCsrRequest(Random.nextBytes(32), generateCsr(keyPair))
        mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(requestCsr)
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()
    }

    @Test
    fun start_create_create_sessionInvalid() = runTest {
        val requestStart = BindingController.BindingParamsRequest("unit test")
        val nonce = Random.Default.nextBytes(32).encodeBase16()
        whenever(nonceToBpkService.exchangeForBpk(eq(nonce))).thenReturn("bpk")
        val keyPair = KeyPairGenerator.getInstance("EC").generateKeyPair()!!

        val startResponse = mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(requestStart)
            header(HttpHeaders.AUTHORIZATION, "Nonce $nonce")
        }.andExpect {
            status { isOk() }
        }.andReturn()
        val xAuthToken = startResponse.response.getHeaderValue(X_AUTH_TOKEN)!!
        val challenge =
            mapper.readValue<BindingController.BindingParamsResponse>(startResponse.response.contentAsString).challenge

        val requestCsr = BindingController.BindingCsrRequest(challenge, generateCsr(keyPair))
        mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(requestCsr)
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(requestCsr)
            header(X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isUnauthorized() }
        }.andReturn()
    }

    private fun generateCsr(keyPair: KeyPair): ByteArray {
        return JcaPKCS10CertificationRequestBuilder(X500Name("CN=Subject"), keyPair.public).build(
            JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
        ).encoded
    }

}