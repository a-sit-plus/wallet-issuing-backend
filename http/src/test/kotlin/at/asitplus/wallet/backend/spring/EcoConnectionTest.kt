package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
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
    private lateinit var authenticationSupplier: AuthenticationSupplier

    private lateinit var bpk: String
    private lateinit var certificate: ByteArray
    private lateinit var client: Client

    @BeforeEach
    fun beforeEach() {
        client = Client()
        // use bpk printed from extNonceService()
        bpk = "dKyd87h31E+oHYLVqpZF+g=="
        certificate = client.selfSignedCert.encoded
        client.storeDeviceBinding(bpk, deviceBindingRepository)
        whenever(authenticationSupplier.getCurrentUserCertificate())
            .thenReturn(certificate)
    }

    @Test
    fun extNonceService() {
        // Get valid nonce manually from https://educard.quarto.at/educard.user/
        val nonce = "82b1979f-edde-48ca-9c74-d272842dd506"

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
