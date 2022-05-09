package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.RevocationService
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.lib.data.AtomicAttributeCredential
import at.asitplus.wallet.lib.data.CredentialSubject
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.Instant
import java.util.UUID
import javax.transaction.Transactional
import kotlin.random.Random

@SpringBootTest
class RevocationServiceStatusListIndexTest {

    @Autowired
    private lateinit var credentialRepo: IssuedCredentialRepository

    @Autowired
    private lateinit var deviceBindingRepository: DeviceBindingRepository

    @MockBean
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
    private lateinit var attributeValue: String
    private lateinit var subjectId: String
    private lateinit var validUntil: Instant
    private lateinit var credentialSubject: CredentialSubject
    private lateinit var issuanceDate: Instant
    private lateinit var expirationDate: Instant

    @BeforeEach
    fun beforeEach() {
        vcId = UUID.randomUUID().toString()
        attributeName = UUID.randomUUID().toString()
        attributeValue = UUID.randomUUID().toString()
        subjectId = UUID.randomUUID().toString()
        credentialSubject = AtomicAttributeCredential(subjectId, attributeName, attributeValue)
        issuanceDate = Instant.now()
        expirationDate = issuanceDate.plusSeconds(60)
        validUntil = Instant.now().plusSeconds(2)
        bpk = UUID.randomUUID().toString()
        certificate = Random.nextBytes(32)
        deviceName = UUID.randomUUID().toString()
        credentialRepo.deleteAll()
        deviceId = UUID.randomUUID().toString()
        deviceBinding = DeviceBinding(bpk, certificate, deviceName, deviceId, validUntil)
        if (deviceBindingRepository.findByCertificateAndRevokedIsFalse(certificate) == null) {
            deviceBinding = deviceBindingRepository.save(deviceBinding)
        }
        whenever(deviceBindingStorageService.getDeviceBindingForCurrentUser())
            .thenReturn(deviceBindingRepository.findAllByBpkAndRevokedIsFalse(bpk).first())
    }

    @Test
    @Transactional
    fun `simple positive add and revoke vcId should work`() {
        revocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate)
        revocationService.isRevoked(vcId) shouldBe false
        revocationService.revokeCredentialsByVcId(vcId) shouldBe 1
        revocationService.isRevoked(vcId) shouldBe true
    }

    @Test
    @Transactional
    fun otherCredentialsForSameDeviceBindingGetRevoked() {
        IssuedCredential(vcId, subjectId, validUntil, deviceBinding, attributeName, 2).also {
            credentialRepo.save(it)
            deviceBinding.issuedCredentialList += it
        }
        IssuedCredential(vcId.reversed(), subjectId.reversed(), validUntil, deviceBinding, attributeName, 1).also {
            credentialRepo.save(it)
            deviceBinding.issuedCredentialList += it
        }
        revocationService.getAllNonRevokedWithDetails().count() shouldBe 2
        whenever(deviceBindingStorageService.getDeviceBindingForCurrentUser())
            .thenReturn(deviceBinding)

        val storeGetNextIndex =
            revocationService.storeGetNextIndex(vcId.drop(2), credentialSubject, issuanceDate, expirationDate)
        storeGetNextIndex.shouldNotBeNull()
        storeGetNextIndex shouldBe 3

        revocationService.getAllNonRevokedWithDetails().count() shouldBe 1
    }

    @Test
    @Transactional
    fun cantIssueCredentialWithSameVcIdTwice() {
        IssuedCredential(vcId, subjectId, validUntil, deviceBinding, attributeName, 3).also {
            credentialRepo.save(it)
        }

        revocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate)
            .shouldBeNull()
    }

    @Test
    @Transactional
    fun `double adding vcId should return null`() {
        revocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate).shouldNotBeNull()
        revocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate).shouldBeNull()
    }

    @Test
    @Transactional
    fun `revocation list should match revocation calls`() {
        val expectedRevocationList = revokeRandomCredentials()

        val revocationList = revocationService.getRevokedStatusListIndexList()

        revocationList shouldBe expectedRevocationList
    }

    private fun revokeRandomCredentials(): MutableList<Int> {
        val expectedRevocationList = mutableListOf<Int>()
        for (i in 1..256) {
            val vcId = UUID.randomUUID().toString()
            val revocationListIndex =
                revocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate)
            if (Random.nextBoolean()) {
                expectedRevocationList.add(revocationListIndex!!)
                revocationService.revokeCredentialsByVcId(vcId)
            }
        }
        return expectedRevocationList
    }

}