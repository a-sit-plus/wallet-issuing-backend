package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.TestTimeSource
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.lib.agent.DefaultCryptoService
import at.asitplus.wallet.lib.agent.HolderAgent
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.MessageWrapper
import at.asitplus.wallet.lib.agent.NextMessage
import at.asitplus.wallet.lib.agent.ProblemReporter
import at.asitplus.wallet.lib.agent.SubjectCredentialStore
import at.asitplus.wallet.lib.data.ConstantIndex
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * Tests the logic (the process) part of the [PupilIdController],
 * i.e. it skips the authentication process entirely by using [WithMockUser].
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(authorities = ["DEVICE_BINDING"])
class PupilIdControllerLogicTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var deviceBindingRepository: DeviceBindingRepository

    @MockBean
    private lateinit var authenticationSupplier: AuthenticationSupplier

    private lateinit var bpk: String
    private lateinit var certificate: ByteArray

    private val subjectCredentialStore = mock<SubjectCredentialStore>()
    private val client = Client()
    private val holderCryptoService = DefaultCryptoService(keyPair = client.keyPair)
    private val holderAgent = HolderAgent.newDefaultInstance(
        subjectCredentialStore = subjectCredentialStore,
        cryptoService = holderCryptoService,
        clock = TestTimeSource.clock
    )
    private val messageWrapper = MessageWrapper(holderCryptoService)
    private val holderMessenger = IssueCredentialMessenger.newHolderInstance(
        holder = holderAgent,
        messageWrapper = messageWrapper,
        credentialScheme = ConstantIndex.PupilId,
        keyId = holderCryptoService.keyId
    )

    @BeforeEach
    fun beforeEach() {
        bpk = UUID.randomUUID().toString()
        certificate = client.selfSignedCert.encoded
        deviceBindingRepository.deleteAll()
        client.storeDeviceBinding(bpk, deviceBindingRepository)
        whenever(authenticationSupplier.getCurrentUserCertificate())
            .thenReturn(certificate)
    }

    @Test
    fun issue_ok() = runTest {
        val request = holderMessenger.startDirect()
        if (request !is NextMessage.Send) throw Exception("Internal Error")

        val response = mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = request.message
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val parsedMessage = holderMessenger.parseMessage(response.response.contentAsString)
        parsedMessage.shouldBeInstanceOf<NextMessage.Result<*>>()
        verify(subjectCredentialStore, times(1)).storeCredential(any(), any())
    }

    @Test
    fun issue_wrongSubject_error() = runTest {
        val client1 = Client()
        certificate = client1.selfSignedCert.encoded
        deviceBindingRepository.deleteAll()
        client1.storeDeviceBinding(bpk, deviceBindingRepository)
        whenever(authenticationSupplier.getCurrentUserCertificate())
            .thenReturn(certificate)
        val request = holderMessenger.startDirect()
        if (request !is NextMessage.Send) throw Exception("Internal Error")

        val response = mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = request.message
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val parsedMessage = holderMessenger.parseMessage(response.response.contentAsString)
        parsedMessage.shouldBeInstanceOf<NextMessage.ReceivedProblemReport>()
        verify(subjectCredentialStore, never()).storeCredential(any(), any())
    }

    @Test
    fun issue_wrongMessage_badRequest() = runTest {
        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = UUID.randomUUID().toString()
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()

        verify(subjectCredentialStore, never()).storeCredential(any(), any())
    }

    @Test
    fun issue_problemReport_ok() = runTest {
        val problemReport = ProblemReporter().problemLastMessage("foo", "unknown")
        val message = messageWrapper.createSignedJwt(problemReport.message)

        mockMvc.post("/pupilid/issue") {
            contentType = MediaType.APPLICATION_JSON
            content = message
        }.andExpect {
            status { isOk() }
            content { string("") }
        }.andReturn()

        verify(subjectCredentialStore, never()).storeCredential(any(), any())
    }

}