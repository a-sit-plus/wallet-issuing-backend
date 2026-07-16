package at.asitplus.wallet.backend.spring

import at.asitplus.openid.OidcUserInfo
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.data.RevokedCredentialRepository
import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.data.AtomicAttribute2023
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.util.*
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@SpringBootTest
class RevocationServiceStatusListIndexTest {

    @Autowired
    private lateinit var credentialRepo: IssuedCredentialRepository

    @Autowired
    private lateinit var revokedCredentialRepo: RevokedCredentialRepository

    @Autowired
    private lateinit var revocationService: RevocationService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var userInfo: OidcUserInfoExtended
    private lateinit var bpk: String
    private lateinit var attributeName: String
    private lateinit var subjectId: String
    private lateinit var validUntil: Instant
    private lateinit var credentialSubject: JsonElement
    private lateinit var issuanceDate: Instant
    private lateinit var expirationDate: Instant
    private lateinit var subjectPublicKey: CryptoPublicKey
    private var timePeriod: Int = 0

    @BeforeEach
    fun beforeEach() {
        userInfo = OidcUserInfoExtended(OidcUserInfo("subject"))
        timePeriod = Random.nextInt(2000, 2032)
        attributeName = ConstantIndex.AtomicAttribute2023.vcType
        subjectId = UUID.randomUUID().toString()
        credentialSubject = AtomicAttribute2023(
            subjectId,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
        ).let {
            joseCompliantSerializer.encodeToJsonElement(it)
        }
        issuanceDate = Clock.System.now()
        expirationDate = Clock.System.now() + 60.seconds
        validUntil = Clock.System.now() + 2.seconds
        bpk = UUID.randomUUID().toString()
        subjectPublicKey = Client().randomKeyAdapter.publicKey
        credentialRepo.deleteAll()
        revokedCredentialRepo.deleteAll()
    }

    @Test
    fun `simple positive add and revoke should work`() = runTest {
        val index = store(timePeriod) shouldBe 1uL
        revocationService.getAllRevokedForUser(userInfo).shouldBeEmpty()
        revocationService.setStatus(timePeriod, index, TokenStatus.Invalid) shouldBe true
        revocationService.getAllRevokedForUser(userInfo).shouldNotBeEmpty()
    }


    @Test
    fun `status list view should include freshly issued credential indexes as valid`() = runTest {
        // Issue enough credentials that the highest index (8, since indexes are 1-based) lands on a byte
        // boundary: with 1-bit statuses packed 8 per byte, index 8 requires a second byte to exist.
        val indexes = (1..8).map { store(timePeriod) }
        indexes.last() shouldBe 8uL

        val statusListView = revocationService.getStatusListView(timePeriod)

        indexes.forEach { index ->
            withClue("status list must be large enough to contain index $index and report it as valid") {
                statusListView.getOrNull(index) shouldBe TokenStatus.Valid
            }
        }
    }

    @Test
    fun `revocation list indexes should be grouped by time period`() = runTest {
        storeNewCredential(timePeriod) shouldBe 1uL
        storeNewCredential(timePeriod) shouldBe 2uL

        storeNewCredential(timePeriod + 1) shouldBe 1uL
        storeNewCredential(timePeriod + 1) shouldBe 2uL
        storeNewCredential(timePeriod + 1) shouldBe 3uL

        storeNewCredential(timePeriod) shouldBe 3uL
    }

    private suspend fun storeNewCredential(timePeriod: Int): ULong = store(timePeriod)

    @Test
    fun `issued credential insert should recover from a drifted identity column`() = runTest {
        store(timePeriod) shouldBe 1uL

        jdbcTemplate.execute("alter table issued_credential alter column id restart with 1")

        store(timePeriod) shouldBe 2uL
        credentialRepo.findAll().size shouldBe 2
    }

    @Test
    fun `revocation list should match revocation calls`() = runTest {
        val expectedRevocationList = revokeRandomCredentials()

        val revocationList = revocationService.getRevokedStatusListIndexList(timePeriod)
        withClue("is:  " + revocationList.joinToString { it.toString() } + "\nref: " + expectedRevocationList.joinToString { it.toString() }) {
            revocationList shouldBe expectedRevocationList
        }
    }

    private suspend fun revokeRandomCredentials(): MutableList<Long> {
        val expectedRevocationList = mutableListOf<Long>()
        (1..256).forEach { _ ->
            val revocationListIndex = store(timePeriod)
            if (Random.nextBoolean()) {
                expectedRevocationList.add(revocationListIndex.toLong())
                revocationService.setStatus(timePeriod, revocationListIndex, TokenStatus.Invalid)
            }
        }
        return expectedRevocationList
    }

    private suspend fun store(timePeriod: Int): ULong {
        val credentialToBeIssued = CredentialToBeIssued.VcJwt(
            subject = credentialSubject,
            expiration = expirationDate,
            scheme = ConstantIndex.AtomicAttribute2023,
            subjectPublicKey = subjectPublicKey,
            userInfo = userInfo
        )
        return revocationService.storeReferencedToken(credentialToBeIssued, timePeriod).getOrThrow().statusListIndex
    }

}

