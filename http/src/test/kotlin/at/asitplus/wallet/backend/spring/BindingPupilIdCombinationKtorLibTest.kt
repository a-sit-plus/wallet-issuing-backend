package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.TestTimeSource
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.lib.agent.*
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.pupilid.*
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.java.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.server.LocalServerPort
import java.security.KeyPair
import java.security.Signature
import java.util.*


/**
 * Simulates a full run of a client for using the [BindingController] and [PupilIdController].
 *
 * Uses the KMM library with ktor to simulate the client.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["backend.time-source=TEST", "backend.authn.device-binding.attestation.noop=true"]
)
@AutoConfigureMockMvc
class BindingPupilIdCombinationKtorLibTest {

    @LocalServerPort
    private var localServerPort: Int = 0

    @MockBean
    private lateinit var extNonceAuthnService: ExtNonceAuthnService

    private lateinit var client: Client
    private lateinit var holderCryptoService: CryptoService
    private lateinit var holderAgent: HolderAgent
    private lateinit var holderMessenger: IssueCredentialMessenger
    private lateinit var bpk: String
    private lateinit var nonce: String
    private lateinit var randomDeviceName: String

    @BeforeEach
    fun beforeEach() {
        client = Client()
        holderCryptoService = DefaultCryptoService(keyPair = client.keyPair)
        holderAgent = HolderAgent.newDefaultInstance(
            cryptoService = holderCryptoService,
            clock = TestTimeSource
        )
        holderMessenger = IssueCredentialMessenger.newHolderInstance(
            holder = holderAgent,
            credentialScheme = ConstantIndex.PupilId,
            keyId = holderCryptoService.keyId,
            messageWrapper = MessageWrapper(holderCryptoService)
        )
        bpk = UUID.randomUUID().toString()
        nonce = UUID.randomUUID().toString()
        randomDeviceName = UUID.randomUUID().toString()

        whenever(extNonceAuthnService.exchangeNonceForBpk(eq(nonce))).thenReturn(bpk)
    }

    @Test
    fun start_create_ok() = runTest {
        val service = createService(client.keyPair)

        val result = service.createDeviceBindingAndIssueCredentials()

        result.shouldBeInstanceOf<ServiceResult.Success>()
        holderMessenger.parseMessage(result.message)
        holderAgent.getCredentials().shouldNotBeEmpty()
    }

    private fun createService(keyPair: KeyPair): DeviceBindingPupilIdIssuingService {
        val deviceAdapter = object : DeviceAdapter {
            override suspend fun createKey(key: KeyAlgorithm, challenge: ByteArray) =
                KmmResult.success(true)

            override suspend fun loadAttestationCerts() = KmmResult.success(listOf<ByteArray>())
            override fun storeCertificate(certificate: ByteArray, attestedPublicKey: String?) =
                KmmResult.success(true)

            override fun getPublicKeyEncoded() = KmmResult.success(keyPair.public.encoded)
            override val deviceName: String = randomDeviceName
        }
        val asn1Adapter = object : Asn1Service.CryptoAdapter {
            override suspend fun sign(
                input: ByteArray,
                key: KeyAlgorithm,
                hash: HashAlgorithm
            ) = KmmResult.success(
                Signature.getInstance("${hash.jcaName}with${key.jcaName}").also {
                    it.initSign(keyPair.private)
                    it.update(input)
                }.sign()
            )
        }
        return DeviceBindingPupilIdIssuingService(
            serverAddress = "http://localhost:$localServerPort",
            extAuthNonce = nonce,
            deviceAdapter = deviceAdapter,
            cryptoAdapter = asn1Adapter,
            callback = { KmmResult.success((holderMessenger.startDirect() as NextMessage.Send).message) },
            serverIssuePath = listOf("pupilid", "issue"),
            engine = Java.create()
        )
    }

    private val HashAlgorithm.jcaName: String
        get() = when (this) {
            HashAlgorithm.SHA1 -> "SHA1"
            HashAlgorithm.SHA256 -> "SHA256"
            HashAlgorithm.SHA512 -> "SHA512"
        }

    private val KeyAlgorithm.jcaName: String
        get() = when (this) {
            KeyAlgorithm.EC -> "ECDSA"
            KeyAlgorithm.RSA -> "RSA"
        }
}
