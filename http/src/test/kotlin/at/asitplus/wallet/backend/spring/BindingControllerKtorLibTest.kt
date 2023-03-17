package at.asitplus.wallet.backend.spring

import at.asitplus.KmmResult
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.pupilid.*
import at.asitplus.wallet.utils.Asn1Service
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.server.LocalServerPort
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.*


/**
 * Simulates a full run of a client for using the [BindingController].
 *
 * Uses the KMM library with ktor to simulate the client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class BindingControllerKtorLibTest {

    @LocalServerPort
    private var localServerPort: Int = 0

    @MockBean
    private lateinit var extNonceAuthnService: ExtNonceAuthnService

    private lateinit var nonce: String
    private lateinit var bpk: String
    private lateinit var randomDeviceName: String

    @BeforeEach
    fun beforeEach() {
        bpk = UUID.randomUUID().toString()
        nonce = UUID.randomUUID().toString()
        randomDeviceName = UUID.randomUUID().toString()

        whenever(extNonceAuthnService.exchangeNonceForBpk(eq(nonce))).thenReturn(bpk)
    }

    @Test
    fun start_create_ok() = runTest {
        val keyPair = KeyPairGenerator.getInstance("EC").generateKeyPair()!!
        val service = createService(keyPair)

        val result = service.createDeviceBinding()

        result.shouldBeInstanceOf<ServiceResult.Success>()
    }

    @Test
    fun start_invalidNonce() = runTest {
        whenever(extNonceAuthnService.exchangeNonceForBpk(eq(nonce))).thenReturn(null)
        val keyPair = KeyPairGenerator.getInstance("EC").generateKeyPair()!!
        val service = createService(keyPair)

        val result = service.createDeviceBinding()

        result.shouldBeInstanceOf<ServiceResult.ErrorFromNetwork>()
    }

    private fun createService(keyPair: KeyPair): DeviceBindingService {
        val deviceAdapter = object : DeviceAdapter {
            override suspend fun createKey(key: KeyAlgorithm, challenge: ByteArray) = KmmResult.success(true)
            override suspend fun loadAttestationCerts(challenge: ByteArray, clientData: ByteArray) =
                KmmResult.success(listOf<ByteArray>())

            override fun storeCertificate(certificate: ByteArray, attestedPublicKey: String?) = KmmResult.success(true)
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

        return DeviceBindingService(
            serverAddress = "http://localhost:$localServerPort",
            extAuthNonce = nonce,
            deviceAdapter = deviceAdapter,
            cryptoAdapter = asn1Adapter,
            httpClientBuilder = HttpClientBuilder(),
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
