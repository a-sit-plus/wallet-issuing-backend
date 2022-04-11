package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.SimpleChallengeService
import at.asitplus.wallet.backend.SimpleDeviceBindingAuthnService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.BadCredentialsException
import java.util.UUID

internal class SimpleDeviceBindingAuthnServiceTest {

    private val challengeService = SimpleChallengeService()
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
        challenge = challengeService.generate()
        deviceName = UUID.randomUUID().toString()
        bpk = UUID.randomUUID().toString()
        client = Client()
    }

    @Test
    fun success() {
        val challengeResponse = client.answerBindingChallenge(challenge)
        deviceBindingStorageService.store(bpk, client.selfSignedCert.encoded, deviceName)

        val result = service.validate(challengeResponse)

        result.shouldNotBeNull()
        result.bpk shouldBe bpk
        result.certificate shouldBe client.selfSignedCert.encoded
    }

    @Test
    fun `wrong certificate in header`() {
        val otherClient = Client()
        val challengeResponse = client.answerBindingChallenge(challenge, otherClient.selfSignedCert.encoded)
        deviceBindingStorageService.store(bpk, client.selfSignedCert.encoded, deviceName)

        shouldThrow<BadCredentialsException> {
            service.validate(challengeResponse)
        }
    }

    @Test
    fun `device binding not known`() {
        val otherClient = Client()
        val challengeResponse = otherClient.answerBindingChallenge(challenge)
        deviceBindingStorageService.store(bpk, client.selfSignedCert.encoded, deviceName)

        val result = service.validate(challengeResponse)

        result.shouldBeNull()
    }

}