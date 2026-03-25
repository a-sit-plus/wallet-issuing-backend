package at.asitplus.wallet.backend.spring

import at.asitplus.openid.OidcUserInfo
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.data.RevokedCredentialRepository
import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.data.AtomicAttribute2023
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.VcDataModelConstants.VERIFIABLE_CREDENTIAL
import at.asitplus.wallet.lib.data.VerifiableCredential
import at.asitplus.wallet.lib.data.VerifiableCredentialJws
import at.asitplus.wallet.lib.data.ktx.extractId
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.wallet.lib.data.vckJsonSerializer
import at.asitplus.wallet.lib.jws.JwsContentTypeConstants
import at.asitplus.wallet.lib.jws.JwsHeaderCertOrJwk
import at.asitplus.wallet.lib.jws.SignJwt
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
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
    private lateinit var vcId: String
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
        userInfo = OidcUserInfoExtended.fromOidcUserInfo(OidcUserInfo("subject")).getOrThrow()
        timePeriod = Random.nextInt(2000, 2032)
        vcId = UUID.randomUUID().toString()
        attributeName = ConstantIndex.AtomicAttribute2023.vcType
        subjectId = UUID.randomUUID().toString()
        credentialSubject = AtomicAttribute2023(
            subjectId,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
        ).let {
            vckJsonSerializer.encodeToJsonElement(it)
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
    fun `simple positive add and revoke vcId should work`() = runTest {
        val index = store(timePeriod, vcId) shouldBe 1uL
        revocationService.isRevoked(vcId, timePeriod) shouldBe false
        revocationService.setStatus(timePeriod, index, TokenStatus.Invalid) shouldBe true
        revocationService.isRevoked(vcId, timePeriod) shouldBe true
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

    private suspend fun storeNewCredential(timePeriod: Int): ULong = store(timePeriod, UUID.randomUUID().toString())

    @Test
    fun `double adding vcId should return null`() = runTest {
        val credentialToBeIssued = CredentialToBeIssued.VcJwt(
            subject = credentialSubject,
            expiration = expirationDate,
            scheme = ConstantIndex.AtomicAttribute2023,
            subjectPublicKey = subjectPublicKey,
            userInfo = userInfo
        )
        val reference = revocationService.createStoredCredentialReference(credentialToBeIssued, timePeriod).getOrThrow()

        revocationService.updateStoredCredential(reference, buildIssuedCredential(vcId)).getOrThrow()
        shouldThrowAny {
            revocationService.updateStoredCredential(reference, buildIssuedCredential(vcId)).getOrThrow()
        }
    }

    @Test
    fun `issued credential insert should recover from a drifted identity column`() = runTest {
        store(timePeriod, UUID.randomUUID().toString()) shouldBe 1uL

        jdbcTemplate.execute("alter table issued_credential alter column id restart with 1")

        store(timePeriod, UUID.randomUUID().toString()) shouldBe 2uL
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
            val vcId = UUID.randomUUID().toString()
            val revocationListIndex = store(timePeriod, vcId)
            if (Random.nextBoolean()) {
                expectedRevocationList.add(revocationListIndex.toLong())
                revocationService.setStatus(timePeriod, revocationListIndex, TokenStatus.Invalid)
            }
        }
        return expectedRevocationList
    }

    private suspend fun store(timePeriod: Int, vcId: String): ULong {
        val credentialToBeIssued = CredentialToBeIssued.VcJwt(
            subject = credentialSubject,
            expiration = expirationDate,
            scheme = ConstantIndex.AtomicAttribute2023,
            subjectPublicKey = subjectPublicKey,
            userInfo = userInfo
        )
        val reference = revocationService.createStoredCredentialReference(credentialToBeIssued, timePeriod).getOrThrow()
        revocationService.updateStoredCredential(reference, buildIssuedCredential(vcId)).getOrThrow()
        return reference.statusListIndex
    }

    private suspend fun buildIssuedCredential(vcId: String): Issuer.IssuedCredential.VcJwt {
        val vc = VerifiableCredential(
            id = vcId,
            issuer = "https://issuer.example.com",
            type = listOf(VERIFIABLE_CREDENTIAL, attributeName),
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            credentialSubject = credentialSubject,
        )
        val vcInJws = SignJwt<VerifiableCredentialJws>(EphemeralKeyWithoutCert(), JwsHeaderCertOrJwk())(
            type = JwsContentTypeConstants.JWT,
            payload = vc.toJws(),
            serializer = VerifiableCredentialJws.serializer(),
        ).getOrThrow()

        return Issuer.IssuedCredential.VcJwt(
            vc = vc,
            signedVcJws = vcInJws,
            scheme = ConstantIndex.AtomicAttribute2023,
            subjectPublicKey = subjectPublicKey,
            userInfo = userInfo,
        )
    }

}

private fun VerifiableCredential.toJws() = VerifiableCredentialJws(
    vc = this,
    subject = credentialSubject.extractId(),
    notBefore = issuanceDate,
    issuer = issuer,
    expiration = expirationDate,
    jwtId = id
)
