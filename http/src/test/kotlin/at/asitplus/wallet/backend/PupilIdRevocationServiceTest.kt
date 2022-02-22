package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertEquals

@SpringBootTest
class PupilIdRevocationServiceTest {

    @Autowired
    private lateinit var credentialRepo: IssuedCredentialRepository

    @Autowired
    private lateinit var deviceBindingStorageService: DeviceBindingStorageService

    @Autowired
    private lateinit var pupilIdRevocationService: PupilIdRevocationService

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
        deviceBinding = deviceBindingStorageService.store(bpk, certificate, deviceName)
        deviceId = deviceBinding.deviceId
    }

    @Test
    fun existingCredentialNotRevoked() {
        IssuedCredential(vcId, subjectId, validUntil, deviceBinding, attributeName).also {
            credentialRepo.save(it)
        }

        assertEquals(false, pupilIdRevocationService.isRevoked(vcId))
    }

    @Test
    fun existingCredentialRevoked() {
        IssuedCredential(vcId, subjectId, validUntil, deviceBinding, attributeName).also {
            it.revoked = true
            credentialRepo.save(it)
        }

        assertEquals(true, pupilIdRevocationService.isRevoked(vcId))
    }

    @Test
    fun revokeExistingCredentialsByBpk() {
        IssuedCredential(vcId, subjectId, validUntil, deviceBinding, attributeName).also {
            credentialRepo.save(it)
        }

        assertEquals(true, pupilIdRevocationService.revokeCredentialsByBpk(bpk))
    }

    @Test
    fun revokeNotExistingCredentialsByBpk() {
        assertEquals(false, pupilIdRevocationService.revokeCredentialsByBpk(bpk))
    }

    @Test
    fun revokeExistingCredentialsByDeviceId() {
        IssuedCredential(vcId, subjectId, validUntil, deviceBinding, attributeName).also {
            credentialRepo.save(it)
        }

        assertEquals(true, pupilIdRevocationService.revokeCredentialsByBpkAndDeviceId(bpk, deviceId))
    }

    @Test
    fun revokeNotExistingCredentialsByDeviceId() {
        assertEquals(false, pupilIdRevocationService.revokeCredentialsByBpkAndDeviceId(bpk, deviceId))
    }

    @Test
    fun revokeExistingCredentialsByWrongDeviceId() {
        IssuedCredential(vcId, subjectId, validUntil, deviceBinding, attributeName).also {
            credentialRepo.save(it)
        }

        assertEquals(
            false,
            pupilIdRevocationService.revokeCredentialsByBpkAndDeviceId(bpk, UUID.randomUUID().toString())
        )
    }

    @Test
    fun revokeExistingCredentialsByWrongBpk() {
        IssuedCredential(vcId, subjectId, validUntil, deviceBinding, attributeName).also {
            credentialRepo.save(it)
        }

        assertEquals(
            false,
            pupilIdRevocationService.revokeCredentialsByBpkAndDeviceId(UUID.randomUUID().toString(), deviceId)
        )
    }

}