package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.RevocationService
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

@SpringBootTest
class RevocationServiceRepositoryTest {

    @Autowired
    private lateinit var credentialRepo: IssuedCredentialRepository

    @Autowired
    private lateinit var deviceBindingStorageService: DeviceBindingStorageService

    @Autowired
    private lateinit var revocationService: RevocationService

    private lateinit var vcId: String
    private lateinit var bpk: String
    private lateinit var certificate: ByteArray
    private lateinit var deviceName: String
    private lateinit var deviceId: String
    private lateinit var deviceBinding: DeviceBinding
    private lateinit var attributeName: String
    private lateinit var subjectId: String
    private lateinit var validUntil: Instant

    @BeforeEach
    fun beforeEach() {
        vcId = UUID.randomUUID().toString()
        attributeName = UUID.randomUUID().toString()
        subjectId = UUID.randomUUID().toString()
        validUntil = Instant.now().plusSeconds(2)
        bpk = UUID.randomUUID().toString()
        certificate = Random.nextBytes(32)
        deviceName = UUID.randomUUID().toString()
        credentialRepo.deleteAll()
        deviceBinding = deviceBindingStorageService.store(bpk, certificate, deviceName)
        deviceId = deviceBinding.deviceId
    }

    @Test
    fun `issued credential should not be revoked`() {
        IssuedCredential(vcId, subjectId, validUntil, deviceBinding, attributeName).also {
            credentialRepo.save(it)
        }

        revocationService.isRevoked(vcId) shouldBe false
    }

    @Test
    fun `issued credential marked as revoked should be revoked`() {
        IssuedCredential(vcId, subjectId, validUntil, deviceBinding, attributeName).also {
            it.revoked = true
            credentialRepo.save(it)
        }

        revocationService.isRevoked(vcId) shouldBe true
    }

    @Test
    fun `revoke credentials by BPK`() {
        IssuedCredential(vcId, subjectId, validUntil, deviceBinding, attributeName).also {
            credentialRepo.save(it)
        }

        revocationService.revokeCredentialsByBpk(bpk) shouldBe 1
    }

    @Test
    fun `revoke non-existing credentials by bpk`() {
        revocationService.revokeCredentialsByBpk(bpk) shouldBe 0
    }

    @Test
    fun `revoke credentials by deviceId`() {
        IssuedCredential(vcId, subjectId, validUntil, deviceBinding, attributeName).also {
            credentialRepo.save(it)
        }

        revocationService.revokeCredentialsByBpkAndDeviceId(bpk, deviceId) shouldBe 1
    }

    @Test
    fun `revoke non-existing credentials by deviceId`() {
        revocationService.revokeCredentialsByBpkAndDeviceId(bpk, deviceId) shouldBe 0
    }

    @Test
    fun `revoke existing credentials by wrong deviceId`() {
        IssuedCredential(vcId, subjectId, validUntil, deviceBinding, attributeName).also {
            credentialRepo.save(it)
        }

        revocationService.revokeCredentialsByBpkAndDeviceId(bpk, UUID.randomUUID().toString()) shouldBe 0
    }

    @Test
    fun `revoke existing credentials by wrong bpk`() {
        IssuedCredential(vcId, subjectId, validUntil, deviceBinding, attributeName).also {
            credentialRepo.save(it)
        }

        revocationService.revokeCredentialsByBpkAndDeviceId(UUID.randomUUID().toString(), deviceId) shouldBe 0
    }

}