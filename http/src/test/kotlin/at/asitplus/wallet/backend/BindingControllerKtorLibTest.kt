package at.asitplus.wallet.backend

import at.asitplus.wallet.Asn1Service
import at.asitplus.wallet.DeviceAdapter
import at.asitplus.wallet.DeviceBindingService
import at.asitplus.wallet.HashAlgorithm
import at.asitplus.wallet.KeyAlgorithm
import at.asitplus.wallet.KmmResult
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.server.LocalServerPort
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.UUID
import kotlin.test.assertIs


/**
 * Simulates a full run of a client for using the [BindingController].
 *
 * Uses the KMM library with ktor to simulate the client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(print = MockMvcPrint.LOG_DEBUG)
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
        nonce = UUID.randomUUID().toString()
        bpk = UUID.randomUUID().toString()
        randomDeviceName = UUID.randomUUID().toString()
        whenever(extNonceAuthnService.exchangeNonceForBpk(eq(nonce))).thenReturn(bpk)
    }

    @Test
    fun start_create_ok() = runTest {
        val keyPair = KeyPairGenerator.getInstance("EC").generateKeyPair()!!
        val deviceAdapter = object : DeviceAdapter {
            override suspend fun createKey(key: KeyAlgorithm, challenge: ByteArray): KmmResult<Boolean> {
                return KmmResult.success(true)
            }

            override suspend fun loadAttestationCerts(): KmmResult<List<ByteArray>> {
                return KmmResult.success(listOf())
            }

            override fun storeCertificate(certificate: ByteArray): KmmResult<Boolean> {
                return KmmResult.success(true)
            }

            override fun getPublicKeyEncoded(): KmmResult<ByteArray> {
                return KmmResult.success(keyPair.public.encoded)
            }

            override val deviceName: String = randomDeviceName
        }
        val asn1Adapter = object : Asn1Service.CryptoAdapter {
            override suspend fun sign(input: ByteArray, key: KeyAlgorithm, hash: HashAlgorithm): KmmResult<ByteArray> {
                return KmmResult.success(
                    Signature.getInstance("${hash.jcaName}with${key.jcaName}").also {
                        it.initSign(keyPair.private)
                        it.update(input)
                    }.sign()
                )
            }
        }

        val service =
            DeviceBindingService(nonce, "http://localhost:$localServerPort", deviceAdapter, Asn1Service(asn1Adapter))
        val result = service.createDeviceBinding()

        assertIs<DeviceBindingService.Result.Success>(result)
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
