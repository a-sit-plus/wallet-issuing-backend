package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.ConstantIndex
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveMinLength
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@Disabled("Would need a valid API-Key in 'application-eco.yml'")
@ActiveProfiles(profiles = ["eco", "pupilid"])
@SpringBootTest
class EcoConnectionTest {

    @Autowired
    private lateinit var extNonceAuthnService: ExtNonceAuthnService

    @Autowired
    private lateinit var issuerCredentialDataProvider: IssuerCredentialDataProvider

    @Autowired
    private lateinit var deviceBindingRepository: DeviceBindingRepository

    @MockBean
    private lateinit var deviceBindingStorageService: DeviceBindingStorageService

    private lateinit var bpk: String
    private lateinit var certificate: ByteArray
    private lateinit var client: Client

    @BeforeEach
    fun beforeEach() {
        client = Client()
        // use bpk printed from extNonceService()
        bpk = "/GtkB4FteZ2IaRf1O8BA9nwNQng=" // or "0bvbtZTc2lzjWwwLD9eYm6DtBts="
        certificate = client.selfSignedCert.encoded
        var deviceBinding = DeviceBinding(bpk, certificate, UUID.randomUUID().toString(), UUID.randomUUID().toString())
        if (deviceBindingRepository.findByCertificateAndRevokedIsFalse(certificate) == null) {
            deviceBinding = deviceBindingRepository.save(deviceBinding)
        }
        whenever(deviceBindingStorageService.getDeviceBindingForCurrentUser())
            .thenReturn(deviceBinding)
    }

    @Test
    fun extNonceService() {
        // Get valid nonce manually from https://educard.quarto.at/educard.user/
        val nonce = "72007150-1dba-466d-9cfa-fe6ee6cc7bd4"

        val bpk = extNonceAuthnService.exchangeNonceForBpk(nonce)
        bpk shouldHaveMinLength 8
        println(bpk)

        val success = extNonceAuthnService.invalidateNonce(nonce)
        success shouldBe true
    }

    @Test
    fun credentialDataProvider() {
        val subjectId = client.keyId
        val credential = issuerCredentialDataProvider.getCredential(subjectId, ConstantIndex.PupilId.vcType)

        credential.shouldNotBeNull()
        println(credential)
    }

}
