package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.*
import kotlin.random.Random
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

    private lateinit var vcId: String
    private lateinit var bpk: String
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
        vcId = UUID.randomUUID().toString()
        attributeName = UUID.randomUUID().toString()
        subjectId = UUID.randomUUID().toString()
        validUntil = Clock.System.now() + 2.seconds
        validUntilExpired = Clock.System.now() - 2.seconds
        bpk = UUID.randomUUID().toString()
        certificate = Random.nextBytes(32)
        deviceName = UUID.randomUUID().toString()
        credentialRepo.deleteAll()
    }

    @Test
    fun `issued credential should not be revoked`() {
        createIssuedCredential()
            .also { credentialRepo.save(it) }

        revocationService.isRevoked(vcId, timePeriod) shouldBe false
    }

    @Test
    fun `revoke credentials by vcId`() {
        createIssuedCredential()
            .also { credentialRepo.save(it) }

        revocationService.setStatus(timePeriod, 1U, TokenStatus.Invalid)
    }

    @Test
    @Disabled("Remnant")
    fun `check on non-existing vcId should return null`() {
        revocationService.isRevoked(vcId, timePeriod).shouldBeNull()
    }

    @Test
    fun `revocation of non-existing vcId should do nothing`() {
        revocationService.setStatus(timePeriod, 1U, TokenStatus.Invalid) shouldBe false
    }

    private fun createIssuedCredential(): IssuedCredential =
        IssuedCredential(
            vcId = vcId,
            subjectId = subjectId,
            userInfoSubject = "userInfoSubject",
            validUntil = validUntil.toJavaInstant(),
            timePeriod = timePeriod,
            attributeName = attributeName,
            revocationListIndex = 1L
        )

}