package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.service.SimpleChallengeService
import at.asitplus.wallet.backend.SimpleDeviceBindingAuthnService
import at.asitplus.wallet.backend.TestTimeSource
import io.matthewnelson.component.base64.encodeBase64
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.util.Base64
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.toKotlinInstant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.BadCredentialsException
import java.util.UUID

class SimpleDeviceBindingAuthnServiceTest {

    private val challengeService = SimpleChallengeService(clock = TestTimeSource)
    private val deviceBindingStorageService = InMemoryDeviceBindingStorageService()
    private val service = SimpleDeviceBindingAuthnService(
        deviceBindingStorageService = deviceBindingStorageService,
        deviceBindingAuthnChallengeService = challengeService
    )

    private lateinit var challenge: ByteArray
    private lateinit var deviceName: String
    private lateinit var bpk: String
    private lateinit var client: Client

    @BeforeEach
    fun beforeEach() {
        challenge =   challengeService.generate()
        deviceName = UUID.randomUUID().toString()
        bpk = UUID.randomUUID().toString()
        client = Client()
        deviceBindingStorageService.store(
            bpk,
            client.selfSignedCert.encoded,
            deviceName,
            client.selfSignedCert.notAfter.toInstant().toKotlinInstant()
        )
    }

    @Test
    fun success() {
        val challengeResponse = client.answerBindingChallenge(challenge)

        val result = service.validate(challengeResponse)

        result.shouldNotBeNull()
        result.bpk shouldBe bpk
        result.certificate shouldBe client.selfSignedCert.encoded
    }

    @Test
    fun `wrong payload`() {
        val jws = JWSObject(
            JWSHeader.Builder(JWSAlgorithm.ES256).x509CertChain(listOf(Base64.encode(client.selfSignedCert.encoded)))
                .build(),
            Payload(challenge.encodeBase64())
        )
        val challengeResponse = client.signBindingChallenge(jws)

        shouldThrow<BadCredentialsException> {
            service.validate(challengeResponse)
        }
    }

    @Test
    fun `wrong payload with wrong key`() {
        val jws = JWSObject(
            JWSHeader.Builder(JWSAlgorithm.ES256).x509CertChain(listOf(Base64.encode(client.selfSignedCert.encoded)))
                .build(),
            Payload(mapOf("challange" to challenge.encodeBase64()))
        )
        val challengeResponse = client.signBindingChallenge(jws)

        shouldThrow<BadCredentialsException> {
            service.validate(challengeResponse)
        }
    }

    @Test
    fun `wrong certificate in header`() {
        val otherClient = Client()
        val challengeResponse = client.answerBindingChallenge(challenge, otherClient.selfSignedCert.encoded)

        shouldThrow<BadCredentialsException> {
            service.validate(challengeResponse)
        }
    }

    @Test
    fun `device binding not known`() {
        val otherClient = Client()
        val challengeResponse = otherClient.answerBindingChallenge(challenge)

        val result = service.validate(challengeResponse)

        result.shouldBeNull()
    }

}