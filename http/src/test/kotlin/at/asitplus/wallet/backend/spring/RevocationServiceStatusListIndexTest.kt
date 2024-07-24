package at.asitplus.wallet.backend.spring

import at.asitplus.crypto.datatypes.CryptoPublicKey
import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.data.RevokedCredentialRepository
import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.data.CredentialSubject
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jakarta.transaction.Transactional
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaInstant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

@SpringBootTest
class RevocationServiceStatusListIndexTest {

    @Autowired
    private lateinit var credentialRepo: IssuedCredentialRepository

    @Autowired
    private lateinit var revokedCredentialRepo: RevokedCredentialRepository

    @Autowired
    private lateinit var revocationService: RevocationService

    private lateinit var vcId: String
    private lateinit var bpk: String
    private lateinit var attributeName: String
    private lateinit var subjectId: String
    private lateinit var validUntil: Instant
    private lateinit var credentialSubject: CredentialSubject
    private lateinit var issuanceDate: Instant
    private lateinit var expirationDate: Instant
    private lateinit var subjectPublicKey: CryptoPublicKey
    private var timePeriod: Int = 0

    @BeforeEach
    fun beforeEach() {
        timePeriod = Random.nextInt(2000, 2032)
        vcId = UUID.randomUUID().toString()
        attributeName = IdAustriaScheme.vcType
        subjectId = UUID.randomUUID().toString()
        credentialSubject = IdAustriaCredential(
            subjectId,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            LocalDate.fromEpochDays(1)
        )
        issuanceDate = Clock.System.now()
        expirationDate = Clock.System.now() + 60.seconds
        validUntil = Clock.System.now() + 2.seconds
        bpk = UUID.randomUUID().toString()
        subjectPublicKey = Client().randomKeyAdapter.publicKey
        credentialRepo.deleteAll()
        revokedCredentialRepo.deleteAll()
    }

    @Test
    @Transactional
    fun `simple positive add and revoke vcId should work`() {
        revocationService.storeGetNextIndex(
            issuanceDate,
            expirationDate,
            timePeriod,
            IssuerCredentialStore.Credential.VcJwt(vcId, credentialSubject, IdAustriaScheme),
            subjectPublicKey
        )
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
        return revocationService.storeGetNextIndex(
            issuanceDate,
            expirationDate,
            i,
            IssuerCredentialStore.Credential.VcJwt(vcId, credentialSubject, IdAustriaScheme),
            subjectPublicKey
        )
    }

    @Test
    @Transactional
    fun cantIssueCredentialWithSameVcIdTwice() {
        IssuedCredential(
            vcId,
            subjectId,
            validUntil.toJavaInstant(),
            timePeriod,
            attributeName,
            3
        ).also {
            credentialRepo.save(it)
        }

        revocationService.storeGetNextIndex(
            issuanceDate,
            expirationDate,
            timePeriod,
            IssuerCredentialStore.Credential.VcJwt(vcId, credentialSubject, IdAustriaScheme),
            subjectPublicKey
        ).shouldBeNull()
    }

    @Test
    @Transactional
    fun `double adding vcId should return null`() {
        revocationService.storeGetNextIndex(
            issuanceDate,
            expirationDate,
            timePeriod,
            IssuerCredentialStore.Credential.VcJwt(vcId, credentialSubject, IdAustriaScheme),
            subjectPublicKey
        ).shouldNotBeNull()
        revocationService.storeGetNextIndex(
            issuanceDate,
            expirationDate,
            timePeriod,
            IssuerCredentialStore.Credential.VcJwt(vcId, credentialSubject, IdAustriaScheme),
            subjectPublicKey
        ).shouldBeNull()
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
                    issuanceDate,
                    expirationDate,
                    timePeriod,
                    IssuerCredentialStore.Credential.VcJwt(vcId, credentialSubject, IdAustriaScheme),
                    subjectPublicKey
                )
            if (Random.nextBoolean()) {
                expectedRevocationList.add(revocationListIndex!!)
                revocationService.revokeCredentialsByVcId(vcId, timePeriod)
            }
        }
        return expectedRevocationList
    }

}