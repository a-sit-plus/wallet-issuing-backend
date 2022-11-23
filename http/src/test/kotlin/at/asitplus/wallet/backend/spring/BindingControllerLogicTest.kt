package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.auth.WebSecurityConstants
import at.asitplus.wallet.backend.auth.WebSecurityConstants.AUTHORITY_PUPIL
import at.asitplus.wallet.pupilid.BindingConfirmRequestJ
import at.asitplus.wallet.pupilid.BindingCsrRequestJ
import at.asitplus.wallet.pupilid.BindingCsrResponseJ
import at.asitplus.wallet.pupilid.BindingParamsRequestJ
import at.asitplus.wallet.pupilid.BindingParamsResponseJ
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.security.cert.CertificateFactory
import java.util.UUID

/**
 * Tests the logic (the process) part of the [BindingController],
 * i.e. it skips the authentication process entirely by using [WithMockUser].
 */
@SpringBootTest
@AutoConfigureMockMvc
class BindingControllerLogicTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @Test
    @WithMockUser(authorities = [AUTHORITY_PUPIL])
    fun start_create_confirm_ok() {
        val client = Client()
        val startRequest = BindingParamsRequestJ(UUID.randomUUID().toString())

        val startResponse = mockMvc.post("/binding/start") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(startRequest)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val bindingParamsResponse = mapper.readValue<BindingParamsResponseJ>(startResponse.response.contentAsString)
        val challenge = bindingParamsResponse.challenge
        val subject = bindingParamsResponse.subject

        val csrRequest = BindingCsrRequestJ(challenge, client.generateCsr(subject), "Unit Test", listOf())

        val createResponse = mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(csrRequest)
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
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

}