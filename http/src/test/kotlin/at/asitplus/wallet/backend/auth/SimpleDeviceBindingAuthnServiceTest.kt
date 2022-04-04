package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.DeviceListEntry
import at.asitplus.wallet.backend.SimpleChallengeService
import at.asitplus.wallet.backend.SimpleDeviceBindingAuthnService
import at.asitplus.wallet.backend.data.DeviceBinding
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
        val deviceBinding = deviceBindingStorageService.store(bpk, client.selfSignedCert.encoded, deviceName)

        val result = service.validate(challengeResponse)

        result.shouldNotBeNull()
        result.bpk shouldBe bpk
        result.certificate shouldBe client.selfSignedCert.encoded
    }

    @Test
    fun `wrong certificate in header`() {
        val otherClient = Client()
        val challengeResponse = client.answerBindingChallenge(challenge, otherClient.selfSignedCert.encoded)
        val deviceBinding = deviceBindingStorageService.store(bpk, client.selfSignedCert.encoded, deviceName)

        shouldThrow<BadCredentialsException> {
            service.validate(challengeResponse)
        }
    }

    @Test
    fun `device binding not known`() {
        val otherClient = Client()
        val challengeResponse = otherClient.answerBindingChallenge(challenge)
        val deviceBinding = deviceBindingStorageService.store(bpk, client.selfSignedCert.encoded, deviceName)

        val result = service.validate(challengeResponse)

        result.shouldBeNull()
    }

    class InMemoryDeviceBindingStorageService : DeviceBindingStorageService {
        private val list = mutableListOf<DeviceBinding>()

        override fun store(bpk: String, certificate: ByteArray, deviceName: String): DeviceBinding {
            return DeviceBinding(bpk, certificate, deviceName, UUID.randomUUID().toString()).also {
                list += it
            }
        }

        override fun lookupBpk(decodedCert: ByteArray): String? {
            return list.firstOrNull { it.certificate.contentEquals(decodedCert) }?.bpk
        }

        override fun lookupDevices(bpk: String): Collection<DeviceListEntry> {
            return list.filter { it.bpk == bpk }.map { DeviceListEntry(it.deviceName, it.deviceId) }
        }

        override fun getDeviceBindingForCurrentUser(): DeviceBinding? {
            return null
        }

    }

}