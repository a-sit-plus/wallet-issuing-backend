package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.data.RevokedCredentialRepository
import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.idaustria.ConstantIndex
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.lib.data.CredentialSubject
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaInstant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.util.UUID
import javax.transaction.Transactional
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

@SpringBootTest
class RevocationServiceStatusListIndexTest {

    @Autowired
    private lateinit var credentialRepo: IssuedCredentialRepository

    @Autowired
    private lateinit var revokedCredentialRepo: RevokedCredentialRepository

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
    private lateinit var subjectId: String
    private lateinit var validUntil: Instant
    private lateinit var credentialSubject: CredentialSubject
    private lateinit var issuanceDate: Instant
    private lateinit var expirationDate: Instant
    private var timePeriod: Int = 0

    @BeforeEach
    fun beforeEach() {
        val client = Client()
        timePeriod = Random.nextInt(2000, 2032)
        vcId = UUID.randomUUID().toString()
        attributeName = ConstantIndex.IdAustriaCredential.vcType
        subjectId = UUID.randomUUID().toString()
        credentialSubject =
            IdAustriaCredential(
                subjectId,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                LocalDate.fromEpochDays(1)
            )
        issuanceDate = Clock.System.now()
        expirationDate = Clock.System.now() + 60.seconds
        validUntil = Clock.System.now() + 2.seconds
        bpk = UUID.randomUUID().toString()
        certificate = client.selfSignedCert.encoded
        credentialRepo.deleteAll()
        revokedCredentialRepo.deleteAll()
        deviceBindingRepository.deleteAll()
        deviceBinding = client.storeDeviceBinding(bpk, deviceBindingRepository)
        whenever(authenticationSupplier.getCurrentUserCertificate())
            .thenReturn(certificate)
    }

    @Test
    @Transactional
    fun `simple positive add and revoke vcId should work`() {
        revocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate, timePeriod)
        revocationService.isRevoked(vcId, timePeriod) shouldBe false
        revocationService.revokeCredentialsByVcId(vcId, timePeriod) shouldBe 1
        revocationService.isRevoked(vcId, timePeriod) shouldBe true
    }


    @Test
    @Transactional
    fun `revocation list indexes should be grouped by time period`() {
        storeNewCredential(timePeriod) shouldBe 1L
        storeNewCredential(timePeriod) shouldBe 2L

        storeNewCredential(timePeriod + 1) shouldBe 1L
        storeNewCredential(timePeriod + 1) shouldBe 2L
        storeNewCredential(timePeriod + 1) shouldBe 3L

        storeNewCredential(timePeriod) shouldBe 3L
    }

    private fun storeNewCredential(i: Int): Long? {
        vcId = UUID.randomUUID().toString()
        return revocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate, i)
    }

    @Test
    @Transactional
    fun otherCredentialsForSameDeviceBindingGetRevoked() {
        IssuedCredential(
            vcId,
            subjectId,
            validUntil.toJavaInstant(),
            timePeriod,
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
            timePeriod,
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
                timePeriod
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
            timePeriod,
            deviceBinding,
            attributeName,
            3
        ).also {
            credentialRepo.save(it)
        }

        revocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate, timePeriod)
            .shouldBeNull()
    }

    @Test
    @Transactional
    fun `double adding vcId should return null`() {
        revocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate, timePeriod)
            .shouldNotBeNull()
        revocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate, timePeriod)
            .shouldBeNull()
    }

    @Test
    @Transactional
    fun `revocation list should match revocation calls`() {
        val expectedRevocationList = revokeRandomCredentials()

        val revocationList = revocationService.getRevokedStatusListIndexList(timePeriod)
        withClue("is:  " + revocationList.joinToString { it.toString() } + "\nref: " + expectedRevocationList.joinToString { it.toString() }) {
            revocationList shouldBe expectedRevocationList
        }
    }

    private fun revokeRandomCredentials(): MutableList<Long> {
        val expectedRevocationList = mutableListOf<Long>()
        for (i in 1..256) {
            val vcId = UUID.randomUUID().toString()
            val revocationListIndex =
                revocationService.storeGetNextIndex(
                    vcId,
                    credentialSubject,
                    issuanceDate,
                    expirationDate,
                    timePeriod
                )
            if (Random.nextBoolean()) {
                expectedRevocationList.add(revocationListIndex!!)
                revocationService.revokeCredentialsByVcId(vcId, timePeriod)
            }
        }
        return expectedRevocationList
    }

}