package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.RevocationService
import at.asitplus.wallet.backend.TestTimeSource
import at.asitplus.wallet.backend.TestTimeSource.javaSchoolYear
import at.asitplus.wallet.backend.TestTimeSource.schoolYear
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.lib.data.AtomicAttributeCredential
import at.asitplus.wallet.lib.data.CredentialSubject
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.TestPropertySource
import java.util.UUID
import javax.transaction.Transactional
import kotlin.properties.Delegates
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

@SpringBootTest
@TestPropertySource(properties = ["backend.time-source=TEST"])
class RevocationServiceStatusListIndexTest {

    @Autowired
    private lateinit var credentialRepo: IssuedCredentialRepository

    @Autowired
    private lateinit var deviceBindingRepository: DeviceBindingRepository

    @MockBean
    private lateinit var authenticationSupplier: AuthenticationSupplier

    @Autowired
    private lateinit var revocationService: RevocationService

    private lateinit var vcId: String
    private lateinit var bpk: String
    private lateinit var certificate: ByteArray
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
        val client = Client()
        vcId = UUID.randomUUID().toString()
        attributeName = UUID.randomUUID().toString()
        attributeValue = UUID.randomUUID().toString()
        subjectId = UUID.randomUUID().toString()
        credentialSubject = AtomicAttributeCredential(subjectId, attributeName, attributeValue)
        issuanceDate = TestTimeSource.now()
        expirationDate = TestTimeSource.now() + 60.seconds
        validUntil = TestTimeSource.now() + 2.seconds
        bpk = UUID.randomUUID().toString()
        certificate = client.selfSignedCert.encoded
        credentialRepo.deleteAll()
        deviceBindingRepository.deleteAll()
        deviceBinding = client.storeDeviceBinding(bpk, deviceBindingRepository)
        whenever(authenticationSupplier.getCurrentUserCertificate())
            .thenReturn(certificate)
    }

    @Test
    @Transactional
    fun `simple positive add and revoke vcId should work`() {
        revocationService.storeGetNextIndex(
            vcId,
            credentialSubject,
            issuanceDate,
            expirationDate,
            schoolYear
        )
        revocationService.isRevoked(vcId, schoolYear) shouldBe false
        revocationService.revokeCredentialsByVcId(vcId, schoolYear) shouldBe 1
        revocationService.isRevoked(vcId, schoolYear) shouldBe true
    }

    @Test
    @Transactional
    fun otherCredentialsForSameDeviceBindingGetRevoked() {
        IssuedCredential(
            vcId,
            subjectId,
            validUntil.toJavaInstant(),
            javaSchoolYear,
            deviceBinding,
            attributeName,
            2
        ).also {
            credentialRepo.save(it)
            deviceBinding.issuedCredentialList += it
        }
        IssuedCredential(
            vcId.reversed(),
            subjectId.reversed(),
            validUntil.toJavaInstant(),
            javaSchoolYear,
            deviceBinding,
            attributeName,
            1
        ).also {
            credentialRepo.save(it)
            deviceBinding.issuedCredentialList += it
        }
        revocationService.getAllNonRevokedWithDetails().count() shouldBe 2

        val storeGetNextIndex =
            revocationService.storeGetNextIndex(
                vcId.drop(2),
                credentialSubject,
                issuanceDate,
                expirationDate,
                schoolYear
            )
        storeGetNextIndex.shouldNotBeNull()
        storeGetNextIndex shouldBe 3

        revocationService.getAllNonRevokedWithDetails().count() shouldBe 1
    }

    @Test
    @Transactional
    fun cantIssueCredentialWithSameVcIdTwice() {
        IssuedCredential(
            vcId,
            subjectId,
            validUntil.toJavaInstant(),
            javaSchoolYear,
            deviceBinding,
            attributeName,
            3
        )
            .also { credentialRepo.save(it) }

        revocationService.storeGetNextIndex(
            vcId,
            credentialSubject,
            issuanceDate,
            expirationDate,
            schoolYear
        )
            .shouldBeNull()
    }

    @Test
    @Transactional
    fun `double adding vcId should return null`() {
        revocationService.storeGetNextIndex(
            vcId,
            credentialSubject,
            issuanceDate,
            expirationDate,
            schoolYear
        ).shouldNotBeNull()
        revocationService.storeGetNextIndex(
            vcId,
            credentialSubject,
            issuanceDate,
            expirationDate,
            schoolYear
        ).shouldBeNull()
    }

    @Test
    @Transactional
    fun `revocation list should match revocation calls`() {
        val expectedRevocationList = revokeRandomCredentials()

        val revocationList = revocationService.getRevokedStatusListIndexList(schoolYear)

        revocationList shouldBe expectedRevocationList
    }

    private fun revokeRandomCredentials(): MutableList<Int> {
        val expectedRevocationList = mutableListOf<Int>()
        for (i in 1..256) {
            val vcId = UUID.randomUUID().toString()
            val revocationListIndex =
                revocationService.storeGetNextIndex(
                    vcId,
                    credentialSubject,
                    issuanceDate,
                    expirationDate,
                    schoolYear
                )
            if (Random.nextBoolean()) {
                expectedRevocationList.add(revocationListIndex!!)
                revocationService.revokeCredentialsByVcId(vcId, schoolYear)
            }
        }
        return expectedRevocationList
    }

}