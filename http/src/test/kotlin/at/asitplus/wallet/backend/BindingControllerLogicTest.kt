package at.asitplus.wallet.backend

import at.asitplus.wallet.BindingConfirmRequestJ
import at.asitplus.wallet.BindingCsrRequestJ
import at.asitplus.wallet.BindingCsrResponseJ
import at.asitplus.wallet.BindingParamsRequestJ
import at.asitplus.wallet.BindingParamsResponseJ
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.CertificateFactory
import java.util.UUID
import kotlin.test.assertContentEquals

/**
 * Tests the logic (the process) part of the [BindingController],
 * i.e. it skips the authentication process entirely by using [WithMockUser].
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.LOG_DEBUG)
class BindingControllerLogicTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @Test
    @WithMockUser(authorities = ["PUPIL"])
    fun start_create_confirm_ok() = runTest {
        val startRequest = BindingParamsRequestJ(UUID.randomUUID().toString())
        val keyPair = KeyPairGenerator.getInstance("EC").generateKeyPair()!!

        val startResponse = mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(startRequest)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val bindingParamsResponse = mapper.readValue<BindingParamsResponseJ>(startResponse.response.contentAsString)
        val challenge = bindingParamsResponse.challenge
        val subject = bindingParamsResponse.subject

        val csrRequest = BindingCsrRequestJ(challenge, generateCsr(keyPair, subject), "Unit Test", listOf())

        val createResponse = mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(csrRequest)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val certBytes = mapper.readValue<BindingCsrResponseJ>(createResponse.response.contentAsString).certificate
        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(certBytes.inputStream())
        assertContentEquals(keyPair.public.encoded, certificate.publicKey.encoded)

        val confirmRequest = BindingConfirmRequestJ(true)

        mockMvc.post("/binding/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(confirmRequest)
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

    private fun generateCsr(keyPair: KeyPair, subject: String): ByteArray {
        return JcaPKCS10CertificationRequestBuilder(X500Name(subject), keyPair.public).build(
            JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
        ).encoded
    }
}