package at.asitplus.wallet.backend.data

import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.RevocationService
import at.asitplus.wallet.lib.data.AtomicAttributeCredential
import at.asitplus.wallet.lib.data.CredentialSubject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.context.support.WithMockUser
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        subjectId = UUID.randomUUID().toString()
        credentialSubject = AtomicAttributeCredential(subjectId, attributeName, "foo")
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
    @WithMockUser(authorities = ["DEVICE_BINDING"])
    fun `revocation of non-existing vcId should do nothing`() {
        assertEquals(0, revocationService.revokeCredentialsByVcId(vcId))
    }

    @Test
    @WithMockUser(authorities = ["DEVICE_BINDING"])
    fun `check on non-existing vcId should return null`() {
        assertNull(revocationService.isRevoked(vcId))
    }

    @Test
    @WithMockUser(authorities = ["DEVICE_BINDING"])
    fun `simple positive add and revoke vcId should work`() {
        revocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate)
        assertEquals(false, revocationService.isRevoked(vcId))
        assertEquals(1, revocationService.revokeCredentialsByVcId(vcId))
        assertEquals(true, revocationService.isRevoked(vcId))
    }

    @Test
    @WithMockUser(authorities = ["DEVICE_BINDING"])
    fun `double adding vcId should return null`() {
        assertNotNull(revocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate))
        assertNull(revocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate))
    }

    @Test
    @WithMockUser(authorities = ["DEVICE_BINDING"])
    fun `revocation list should match revocation calls`() {
        val expectedRevocationList = revokeRandomCredentials()

        val revocationList = revocationService.getRevokedStatusListIndexList()
        assertContentEquals(expectedRevocationList, revocationList, "Revocation list should match revocation calls")
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