package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.RevocationService
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.time.Year
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
    private lateinit var validUntilExpired: Instant

    @BeforeEach
    fun beforeEach() {
        vcId = UUID.randomUUID().toString()
        attributeName = UUID.randomUUID().toString()
        subjectId = UUID.randomUUID().toString()
        validUntil = Instant.now().plusSeconds(2)
        validUntilExpired = Instant.now().minusSeconds(2)
        bpk = UUID.randomUUID().toString()
        certificate = Random.nextBytes(32)
        deviceName = UUID.randomUUID().toString()
        credentialRepo.deleteAll()
        if (deviceBindingStorageService.lookupBpk(certificate) == null)
            deviceBinding = deviceBindingStorageService.store(bpk, certificate, deviceName, validUntil)
        deviceId = deviceBinding.deviceId
    }

    @Test
    fun `issued credential should not be revoked`() {
        createIssuedCredential()
            .also { credentialRepo.save(it) }

        revocationService.isRevoked(vcId,2021) shouldBe false
    }

    @Test
    fun `expired credential should not be revoked`() {
        createExpiredCredential()
            .also { credentialRepo.save(it) }

        revocationService.isRevoked(vcId,2021) shouldBe false
    }

    @Test
    fun `issued credential marked as revoked should be revoked`() {
        createIssuedCredential().also {
            it.revoked = true
            credentialRepo.save(it)
        }

        revocationService.isRevoked(vcId,2021) shouldBe true
    }

    @Test
    fun `expired credential marked as revoked should be revoked`() {
        createExpiredCredential().also {
            it.revoked = true
            credentialRepo.save(it)
        }

        revocationService.isRevoked(vcId,2021) shouldBe true
    }

    @Test
    fun `revoke credentials by vcId`() {
        createIssuedCredential()
            .also { credentialRepo.save(it) }

        revocationService.revokeCredentialsByVcId(vcId,2021) shouldBe 1
    }

    @Test
    fun `check on non-existing vcId should return null`() {
        revocationService.isRevoked(vcId,2021).shouldBeNull()
    }

    @Test
    fun `revocation of non-existing vcId should do nothing`() {
        revocationService.revokeCredentialsByVcId(vcId,2021) shouldBe 0
    }

    @Test
    fun `revoke credentials by bpk`() {
        createIssuedCredential()
            .also { credentialRepo.save(it) }

        revocationService.revokeCredentialsByBpk(bpk) shouldBe 1
    }

    @Test
    fun `revoke expired credentials by bpk`() {
        createExpiredCredential()
            .also { credentialRepo.save(it) }

        revocationService.revokeCredentialsByBpk(bpk) shouldBe 0
    }

    @Test
    fun `revoke non-existing credentials by bpk`() {
        revocationService.revokeCredentialsByBpk(bpk) shouldBe 0
    }

    @Test
    fun `revoke credentials by deviceId`() {
        createIssuedCredential()
            .also { credentialRepo.save(it) }

        revocationService.revokeCredentialsByBpkAndDeviceId(bpk, deviceId) shouldBe 1
    }

    @Test
    fun `revoke expired credentials by deviceId`() {
        createExpiredCredential()
            .also { credentialRepo.save(it) }

        revocationService.revokeCredentialsByBpkAndDeviceId(bpk, deviceId) shouldBe 0
    }

    @Test
    fun `revoke non-existing credentials by deviceId`() {
        revocationService.revokeCredentialsByBpkAndDeviceId(bpk, deviceId) shouldBe 0
    }

    @Test
    fun `revoke existing credentials by wrong deviceId`() {
        createIssuedCredential()
            .also { credentialRepo.save(it) }

        revocationService.revokeCredentialsByBpkAndDeviceId(bpk, UUID.randomUUID().toString()) shouldBe 0
    }

    @Test
    fun `revoke existing credentials by wrong bpk`() {
        createIssuedCredential()
            .also { credentialRepo.save(it) }

        revocationService.revokeCredentialsByBpkAndDeviceId(UUID.randomUUID().toString(), deviceId) shouldBe 0
    }

    private fun createIssuedCredential(): IssuedCredential =
        IssuedCredential(vcId, subjectId, validUntil, Year.of(2021), deviceBinding, attributeName, 1L)

    private fun createExpiredCredential(): IssuedCredential =
        IssuedCredential(vcId, subjectId, validUntilExpired, Year.of(2021),deviceBinding, attributeName, 1L)

}