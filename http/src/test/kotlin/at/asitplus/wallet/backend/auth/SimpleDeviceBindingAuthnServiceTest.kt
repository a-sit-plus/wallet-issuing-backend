package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.ClientCertificateService
import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.DeviceListEntry
import at.asitplus.wallet.backend.SimpleChallengeService
import at.asitplus.wallet.backend.SimpleDeviceBindingAuthnService
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.lib.encodeBase64
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.util.Base64
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.BadCredentialsException
import java.security.PrivateKey
import java.security.interfaces.ECPrivateKey
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
    private lateinit var clientCertificateService: ClientCertificateService
    private lateinit var clientCert: ByteArray
    private lateinit var clientPrivateKey: PrivateKey

    @BeforeEach
    fun beforeEach() {
        challenge = challengeService.generate()
        deviceName = UUID.randomUUID().toString()
        bpk = UUID.randomUUID().toString()
        clientCertificateService = ClientCertificateService()
        clientCert = clientCertificateService.cert.encoded
        clientPrivateKey = clientCertificateService.keyPair.private
    }

    @Test
    fun success() {
        val challengeResponse = calcChallengeResponse(challenge, clientCert, clientPrivateKey)
        val deviceBinding = deviceBindingStorageService.store(bpk, clientCert, deviceName)

        val result = service.validate(challengeResponse)

        result.shouldNotBeNull()
        result.bpk shouldBe bpk
        result.certificate shouldBe clientCert
    }

    @Test
    fun `wrong certificate in header`() {
        val otherClientCertificateService = ClientCertificateService()
        val challengeResponse =
            calcChallengeResponse(challenge, otherClientCertificateService.cert.encoded, clientPrivateKey)
        val deviceBinding = deviceBindingStorageService.store(bpk, clientCert, deviceName)

        shouldThrow<BadCredentialsException> {
            service.validate(challengeResponse)
        }
    }

    @Test
    fun `device binding not known`() {
        val otherClientCertificateService = ClientCertificateService()
        val challengeResponse = calcChallengeResponse(
            challenge,
            otherClientCertificateService.cert.encoded,
            otherClientCertificateService.keyPair.private
        )
        val deviceBinding = deviceBindingStorageService.store(bpk, clientCert, deviceName)

        val result = service.validate(challengeResponse)

        result.shouldBeNull()
    }

    private fun calcChallengeResponse(
        challenge: ByteArray,
        clientCert: ByteArray,
        clientPrivateKey: PrivateKey
    ): String = JWSObject(
        JWSHeader.Builder(JWSAlgorithm.ES256).x509CertChain(listOf(Base64.encode(clientCert))).build(),
        Payload(mapOf("challenge" to challenge.encodeBase64()))
    ).also {
        it.sign(ECDSASigner(clientPrivateKey as ECPrivateKey))
    }.serialize()


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