package at.asitplus.wallet.backend.data

import at.asitplus.wallet.backend.PupilIdRevocationService
import at.asitplus.wallet.backend.auth.AuthenticatedDeviceBindingUser
import at.asitplus.wallet.backend.data.PupilIdRevocationServiceTest.UserDetailsServiceInt.certificate
import at.asitplus.wallet.lib.data.AtomicAttributeCredential
import at.asitplus.wallet.lib.data.CredentialSubject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.test.context.support.WithUserDetails
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@SpringBootTest(classes = [PupilIdRevocationServiceTest.TestConfig::class])
@AutoConfigureTestDatabase
class PupilIdRevocationServiceTest {

    @Autowired
    private lateinit var pupilIdRevocationService: PupilIdRevocationService

    @Autowired
    private lateinit var deviceBindingRepository: DeviceBindingRepository

    private lateinit var vcId: String
    private lateinit var attributeName: String
    private lateinit var subjectId: String
    private lateinit var credentialSubject: CredentialSubject
    private lateinit var bpk: String
    private lateinit var certificate: ByteArray
    private lateinit var issuanceDate: kotlinx.datetime.Instant
    private lateinit var expirationDate: kotlinx.datetime.Instant

    /**
     * Workaround to be able to read the random [certificate] (for other mock beans),
     * that will be used in the user details of the authenticated user.
     */
    private object UserDetailsServiceInt : UserDetailsService {

        val bpk = UUID.randomUUID().toString()
        val certificate: ByteArray = Random.nextBytes(32)

        override fun loadUserByUsername(username: String): UserDetails {
            return AuthenticatedDeviceBindingUser(bpk, certificate)
        }
    }

    /**
     * Class needed to define a bean called [userDetailsServiceInt] that
     * can be picked up by the [WithUserDetails] annotation in a test case
     */
    @TestConfiguration
    internal class TestConfig {
        @Bean
        fun userDetailsServiceInt(): UserDetailsService {
            return UserDetailsServiceInt
        }
    }

    @BeforeEach
    fun beforeEach() {
        vcId = UUID.randomUUID().toString()
        attributeName = UUID.randomUUID().toString()
        subjectId = UUID.randomUUID().toString()
        credentialSubject = AtomicAttributeCredential(subjectId, attributeName, "foo")
        issuanceDate = kotlinx.datetime.Clock.System.now()
        expirationDate = issuanceDate + 60.seconds
        bpk = UserDetailsServiceInt.bpk
        certificate = UserDetailsServiceInt.certificate
        val deviceName = UUID.randomUUID().toString()
        val deviceId = UUID.randomUUID().toString()
        if (deviceBindingRepository.findByCertificate(certificate) == null) {
            deviceBindingRepository.save(DeviceBinding(bpk, certificate, deviceName, deviceId))
        }
    }

    @Test
    @WithUserDetails(userDetailsServiceBeanName = "userDetailsServiceInt")
    fun `revocation of non-existing vcId should do nothing`() {
        assertFalse(pupilIdRevocationService.revokeCredentialsByVcId(vcId))
    }

    @Test
    @WithUserDetails(userDetailsServiceBeanName = "userDetailsServiceInt")
    fun `check on non-existing vcId should return null`() {
        assertNull(pupilIdRevocationService.isRevoked(vcId))
    }

    @Test
    @WithUserDetails(userDetailsServiceBeanName = "userDetailsServiceInt")
    fun `simple positive add and revoke vcId should work`() {
        pupilIdRevocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate)
        assertEquals(false, pupilIdRevocationService.isRevoked(vcId))
        assertTrue(pupilIdRevocationService.revokeCredentialsByVcId(vcId))
        assertEquals(true, pupilIdRevocationService.isRevoked(vcId))
    }

    @Test
    @WithUserDetails(userDetailsServiceBeanName = "userDetailsServiceInt")
    fun `double adding vcId should return null`() {
        assertNotNull(pupilIdRevocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate))
        assertNull(pupilIdRevocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate))
    }

    @Test
    @WithUserDetails(userDetailsServiceBeanName = "userDetailsServiceInt")
    fun `revocation list should match revocation calls`() {
        val expectedRevocationList = revokeRandomCredentials()

        val revocationList = pupilIdRevocationService.getRevokedStatusListIndexList()
        assertContentEquals(expectedRevocationList, revocationList, "Revocation list should match revocation calls")
    }

    private fun revokeRandomCredentials(): MutableList<Int> {
        val expectedRevocationList = mutableListOf<Int>()
        for (i in 1..256) {
            val vcId = UUID.randomUUID().toString()
            val revocationListIndex =
                pupilIdRevocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate)
            if (Random.nextBoolean()) {
                expectedRevocationList.add(revocationListIndex!!)
                pupilIdRevocationService.revokeCredentialsByVcId(vcId)
            }
        }
        return expectedRevocationList
    }

}