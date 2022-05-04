package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.RevocationService
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.lib.data.AtomicAttributeCredential
import at.asitplus.wallet.lib.data.CredentialSubject
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

@SpringBootTest
@AutoConfigureTestDatabase
class RevocationServiceTest {

    @Autowired
    private lateinit var revocationService: RevocationService

    @Autowired
    private lateinit var deviceBindingRepository: DeviceBindingRepository

    @MockBean
    private lateinit var deviceBindingStorageService: DeviceBindingStorageService

    private lateinit var vcId: String
    private lateinit var attributeName: String
    private lateinit var attributeValue: String
    private lateinit var subjectId: String
    private lateinit var credentialSubject: CredentialSubject
    private lateinit var bpk: String
    private lateinit var certificate: ByteArray
    private lateinit var issuanceDate: kotlinx.datetime.Instant
    private lateinit var expirationDate: kotlinx.datetime.Instant

    @BeforeEach
    fun beforeEach() {
        vcId = UUID.randomUUID().toString()
        attributeName = UUID.randomUUID().toString()
        attributeValue = UUID.randomUUID().toString()
        subjectId = UUID.randomUUID().toString()
        credentialSubject = AtomicAttributeCredential(subjectId, attributeName, attributeValue)
        issuanceDate = kotlinx.datetime.Clock.System.now()
        expirationDate = issuanceDate + 60.seconds
        bpk = UUID.randomUUID().toString()
        certificate = Random.nextBytes(32)
        val deviceName = UUID.randomUUID().toString()
        val deviceId = UUID.randomUUID().toString()
        var deviceBinding = DeviceBinding(bpk, certificate, deviceName, deviceId)
        if (deviceBindingRepository.findByCertificate(certificate) == null) {
            deviceBinding = deviceBindingRepository.save(deviceBinding)
        }
        whenever(deviceBindingStorageService.getDeviceBindingForCurrentUser())
            .thenReturn(deviceBinding)
    }

    @Test
    fun `revocation of non-existing vcId should do nothing`() {
        revocationService.revokeCredentialsByVcId(vcId) shouldBe 0
    }

    @Test
    fun `check on non-existing vcId should return null`() {
        revocationService.isRevoked(vcId).shouldBeNull()
    }

    @Test
    fun `simple positive add and revoke vcId should work`() {
        revocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate)
        revocationService.isRevoked(vcId) shouldBe false
        revocationService.revokeCredentialsByVcId(vcId) shouldBe 1
        revocationService.isRevoked(vcId) shouldBe true
    }

    @Test
    fun `double adding vcId should return null`() {
        revocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate).shouldNotBeNull()
        revocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate).shouldBeNull()
    }

    @Test
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