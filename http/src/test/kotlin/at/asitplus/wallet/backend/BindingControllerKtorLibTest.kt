package at.asitplus.wallet.backend

import at.asitplus.wallet.DeviceAdapter
import at.asitplus.wallet.DeviceBindingService
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.server.LocalServerPort
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.UUID
import kotlin.test.assertIs
import kotlin.test.assertNotNull

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
            override val deviceName = randomDeviceName

            override suspend fun createKeyCsr(challenge: ByteArray): ByteArray {
                return generateCsr(keyPair)
            }

            override fun loadAttestationCerts(): List<ByteArray> {
                return listOf()
            }

            override fun storeCertificate(certificate: ByteArray) {
                assertNotNull(certificate)
            }
        }
        val service = DeviceBindingService(nonce, "http://localhost:$localServerPort", deviceAdapter)
        val result = service.createDeviceBinding()

        assertIs<DeviceBindingService.Result.Success>(result)
    }

    private fun generateCsr(keyPair: KeyPair): ByteArray {
        return JcaPKCS10CertificationRequestBuilder(X500Name("CN=Subject"), keyPair.public).build(
            JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
        ).encoded
    }

}
