package at.asitplus.wallet.backend

import at.asitplus.wallet.PupilIdIssuingService
import at.asitplus.wallet.ServiceResult
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.lib.KmmResult
import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.agent.DefaultCryptoService
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.NextMessage
import at.asitplus.wallet.lib.data.ConstantIndex
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import java.util.UUID
import kotlin.test.assertIs

/**
 * Simulates a full run of a client using the [PupilIdController].
 *
 * Uses the KMM library with ktor to simulate the client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(print = MockMvcPrint.LOG_DEBUG)
class PupilIdControllerKtorLibTest {

    @LocalServerPort
    private var localServerPort: Int = 0

    @Autowired
    private lateinit var deviceBindingRepository: DeviceBindingRepository

    private lateinit var subjectAgent: Agent
    private lateinit var subjectMessenger: IssueCredentialMessenger
    private lateinit var request: NextMessage.Send
    private lateinit var clientCert: ByteArray

    @BeforeEach
    fun beforeEach() {
        val client = Client()
        subjectAgent = Agent(cryptoService = DefaultCryptoService(client.keyPair))
        subjectMessenger = IssueCredentialMessenger(agent = subjectAgent, credentialScheme = ConstantIndex.PupilId)
        val bpk = UUID.randomUUID().toString()
        val deviceName = UUID.randomUUID().toString()
        val deviceId = UUID.randomUUID().toString()
        clientCert = client.selfSignedCert.encoded
        deviceBindingRepository.save(DeviceBinding(bpk, clientCert, deviceName, deviceId))
    }

    @Test
    fun start_challengeResponse_ok() = runTest {
        request = subjectMessenger.startDirect() as NextMessage.Send
        val cryptoAdapter = object : PupilIdIssuingService.CryptoAdapter {
            override fun getCertificateChain(): KmmResult<Array<ByteArray>> = KmmResult.success(arrayOf(clientCert))
        }

        val service =
            PupilIdIssuingService("http://localhost:$localServerPort", subjectAgent.cryptoService, cryptoAdapter)
        val result = service.issueCredentials(request.message)

        assertIs<ServiceResult.Success>(result)
    }

}