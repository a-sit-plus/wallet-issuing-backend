package at.asitplus.wallet.backend.spring

import at.asitplus.openid.OidcUserInfo
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.*
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@SpringBootTest
class RevocationServiceRepositoryTest {

    @Autowired
    private lateinit var credentialRepo: IssuedCredentialRepository

    @Autowired
    private lateinit var revocationService: RevocationService

    private lateinit var userInfoSubject: String
    private lateinit var certificate: ByteArray
    private lateinit var deviceName: String
    private lateinit var attributeName: String
    private lateinit var subjectId: String
    private lateinit var validUntil: Instant
    private lateinit var validUntilExpired: Instant
    private var timePeriod: Int = 0

    @BeforeEach
    fun beforeEach() {
        timePeriod = Random.nextInt(2000, 2032)
        attributeName = UUID.randomUUID().toString()
        subjectId = UUID.randomUUID().toString()
        validUntil = Clock.System.now() + 2.seconds
        validUntilExpired = Clock.System.now() - 2.seconds
        userInfoSubject = UUID.randomUUID().toString()
        certificate = Random.nextBytes(32)
        deviceName = UUID.randomUUID().toString()
        credentialRepo.deleteAll()
    }

    @Test
    fun `issued credential should not be revoked`() {
        val stored = createIssuedCredential()
            .also { credentialRepo.save(it) }

        revocationService.getAllRevokedForUser(OidcUserInfoExtended(OidcUserInfo(userInfoSubject)))
            .filter { it.revocationListIndex == stored.revocationListIndex }
            .shouldBeEmpty()
    }

    @Test
    fun `revoke credentials by revocationListIndex`() {
        val stored = createIssuedCredential()
            .also { credentialRepo.save(it) }

        revocationService.setStatus(timePeriod, 1U, TokenStatus.Invalid)
        revocationService.getAllRevokedForUser(OidcUserInfoExtended(OidcUserInfo(userInfoSubject)))
            .filter { it.revocationListIndex == stored.revocationListIndex }
            .shouldNotBeEmpty()
    }

    @Test
    fun `check on non-existing user should return empty list`() {
        revocationService.getAllRevokedForUser(OidcUserInfoExtended(OidcUserInfo(userInfoSubject.reversed())))
            .shouldBeEmpty()
    }

    @Test
    fun `revocation of non-existing vcId should do nothing`() {
        revocationService.setStatus(
            timePeriod,
            Random.nextULong(1U, Long.MAX_VALUE.toULong()),
            TokenStatus.Invalid
        ) shouldBe false
    }

    private fun createIssuedCredential(): IssuedCredential =
        IssuedCredential(
            subjectId = subjectId,
            userInfoSubject = userInfoSubject,
            validUntil = validUntil.toJavaInstant(),
            timePeriod = timePeriod,
            attributeName = attributeName,
            revocationListIndex = 1L
        )

}