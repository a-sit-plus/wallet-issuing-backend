package at.asitplus.wallet.backend.spring

import at.asitplus.KmmResult
import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.lib.agent.*
import at.asitplus.wallet.lib.aries.IssueCredentialMessenger
import at.asitplus.wallet.lib.aries.MessageWrapper
import at.asitplus.wallet.lib.aries.NextMessage
import at.asitplus.wallet.lib.jws.DefaultJwsService
import at.asitplus.wallet.lib.jws.JwsHeader
import at.asitplus.wallet.pupilid.*
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.util.*

/**
 * Simulates a full run of a client using the [PupilIdController].
 *
 * Uses the KMM library with ktor to simulate the client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PupilIdControllerKtorLibTest {

    @LocalServerPort
    private var localServerPort: Int = 0

    @Autowired
    private lateinit var deviceBindingRepository: DeviceBindingRepository

    private lateinit var holderCryptoService: CryptoService
    private lateinit var holderAgent: HolderAgent
    private lateinit var holderMessenger: IssueCredentialMessenger
    private lateinit var clientCert: ByteArray

    @BeforeEach
    fun beforeEach() {
        Initializer.initWithVcLib()
        val client = Client()
        holderCryptoService = DefaultCryptoService(client.keyPair)
        holderAgent = HolderAgent.newDefaultInstance(holderCryptoService)
        holderMessenger = IssueCredentialMessenger.newHolderInstance(
            holder = holderAgent,
            credentialScheme = ConstantIndex.PupilId,
            messageWrapper = MessageWrapper(holderCryptoService)
        )
        val bpk = UUID.randomUUID().toString()
        clientCert = client.selfSignedCert.encoded
        client.storeDeviceBinding(bpk, deviceBindingRepository)
    }

    @Test
    fun issue_challengeResponse_ok() = runTest {
        val request = holderMessenger.startDirect() as NextMessage.Send
        val cryptoAdapter = PupilIdIssuingService.JwsAdapter { payload ->
            KmmResult.success(
                DefaultJwsService(holderCryptoService).createSignedJws(
                    header = JwsHeader(
                        algorithm = holderCryptoService.jwsAlgorithm,
                        keyId = holderCryptoService.toJsonWebKey().keyId,
                        certificateChain = arrayOf(clientCert)
                    ),
                    payload = payload.encodeToByteArray()
                )!!
            )
        }

        val service = PupilIdIssuingService(
            "http://localhost:$localServerPort",
            cryptoAdapter,
            httpClientBuilder = HttpClientBuilder()
        )
        val result = service.issueCredentials(request.message)

        result.shouldBeInstanceOf<ServiceResult.Success>()
    }

    @Test
    fun issue_wrongAuth_error() = runTest {
        val request = holderMessenger.startDirect() as NextMessage.Send
        val cryptoAdapter = PupilIdIssuingService.JwsAdapter { payload ->
            KmmResult.success(
                DefaultJwsService(holderCryptoService).createSignedJws(
                    header = JwsHeader(
                        algorithm = holderCryptoService.jwsAlgorithm,
                        keyId = holderCryptoService.toJsonWebKey().keyId,
                        certificateChain = arrayOf(clientCert)
                    ),
                    payload = payload.encodeToByteArray().reversedArray()
                )!!
            )
        }

        val service = PupilIdIssuingService(
            "http://localhost:$localServerPort",
            cryptoAdapter,
            httpClientBuilder = HttpClientBuilder()
        )
        val result = service.issueCredentials(request.message)

        result.shouldBeInstanceOf<ServiceResult.ErrorFromNetwork>()
        val details = result.details
        details.shouldNotBeNull()
        details.status shouldBe 401
        details.path shouldBe "/pupilid/issue"
        details.error shouldBe "Unauthorized"
    }

}