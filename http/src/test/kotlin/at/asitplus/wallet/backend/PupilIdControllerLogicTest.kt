package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.MessageWrapper
import at.asitplus.wallet.lib.agent.NextMessage
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.decodeBase64ToArray
import at.asitplus.wallet.lib.encodeBase64
import at.asitplus.wallet.lib.jvm.AgentJvm
import at.asitplus.wallet.lib.jvm.JwsServiceJvm
import at.asitplus.wallet.lib.jvm.KeyIdServiceJvm
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.util.Base64
import kotlinx.coroutines.test.runTest
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
import java.security.PrivateKey
import java.security.interfaces.ECPrivateKey
import java.util.UUID
import kotlin.test.assertIs

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.LOG_DEBUG)
class PupilIdControllerLogicTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var deviceBindingStorageService: DeviceBindingStorageService

    private val subjectAgent = AgentJvm.new()
    private val subjectMessenger = IssueCredentialMessenger(
        agent = subjectAgent,
        messageWrapper = MessageWrapper(subjectAgent.cryptoService, KeyIdServiceJvm(), JwsServiceJvm()),
        credentialScheme = ConstantIndex.PupilId
    )

    @Test
    fun start_challengeResponse_ok() = runTest {
        val request = subjectMessenger.startDirect()
        if (request !is NextMessage.Send) throw Exception("Internal Error")
        val clientCertificateService = ClientCertificateService()
        val bpk = UUID.randomUUID().toString()
        whenever(deviceBindingStorageService.lookupBpk(eq(clientCertificateService.cert.encoded))).thenReturn(bpk)

        val firstResponse = mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = request.message
        }.andExpect {
            status { isUnauthorized() }
            header { exists(HttpHeaders.WWW_AUTHENTICATE) }
        }.andReturn()

        val headerValue = firstResponse.response.getHeaderValue(HttpHeaders.WWW_AUTHENTICATE)
        val challenge = headerValue.toString().removePrefix("Challenge ").decodeBase64ToArray()!!
        val challengeResponse = calcChallengeResponse(
            challenge,
            clientCertificateService.cert.encoded,
            clientCertificateService.keyPair.private
        )

        val response = mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = request.message
            header(HttpHeaders.AUTHORIZATION, "Response $challengeResponse")
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val parsedMessage = subjectMessenger.parseMessage(response.response.contentAsString)
        assertIs<NextMessage.Finished>(parsedMessage)
    }

    private fun calcChallengeResponse(challenge: ByteArray, certificate: ByteArray, privateKey: PrivateKey): String {
        return JWSObject(
            JWSHeader.Builder(JWSAlgorithm.ES256).x509CertChain(listOf(Base64.encode(certificate))).build(),
            Payload(mapOf("challenge" to challenge.encodeBase64()))
        ).also {
            it.sign(ECDSASigner(privateKey as ECPrivateKey))
        }.serialize()
    }

}