package at.asitplus.wallet.backend.spring

import at.asitplus.openid.OidcUserInfo
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.data.RevokedCredentialRepository
import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.backend.service.StatusListChangedEvent
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.data.AtomicAttribute2023
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import java.util.*
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@SpringBootTest(
    properties = [
        "backend.revocation-list.capacity-increment=8",
        "backend.revocation-list.minimum-free-indices=4",
    ]
)
@RecordApplicationEvents
class RevocationServiceStatusListCapacityTest {

    @Autowired
    private lateinit var credentialRepo: IssuedCredentialRepository

    @Autowired
    private lateinit var revokedCredentialRepo: RevokedCredentialRepository

    @Autowired
    private lateinit var revocationService: RevocationService

    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

    private var timePeriod: Int = 0

    @BeforeEach
    fun beforeEach() {
        timePeriod = Random.nextInt(2000, 2032)
        credentialRepo.deleteAll()
        revokedCredentialRepo.deleteAll()
    }

    @Test
    fun `status list view should contain valid entries for indexes not yet assigned`() = runTest {
        store() shouldBe 1uL

        val statusListView = revocationService.getStatusListView(timePeriod)

        (1uL..7uL).forEach { statusListView.getOrNull(it) shouldBe TokenStatus.Valid }
        statusListView.getOrNull(8uL) shouldBe null
    }

    @Test
    fun `status list should only be marked as changed when issuing uses up the free indexes`() = runTest {
        // indexes 4..7 are still free after issuing 3 credentials
        repeat(3) { store() }
        applicationEvents.stream(StatusListChangedEvent::class.java).toList().shouldBeEmpty()

        store() shouldBe 4uL
        applicationEvents.stream(StatusListChangedEvent::class.java).toList()
            .shouldHaveSize(1).first().timePeriod shouldBe timePeriod
        revocationService.getStatusListView(timePeriod).getOrNull(15uL) shouldBe TokenStatus.Valid
    }

    private suspend fun store(): ULong = revocationService.storeReferencedToken(
        CredentialToBeIssued.VcJwt(
            subject = joseCompliantSerializer.encodeToJsonElement(
                AtomicAttribute2023(UUID.randomUUID().toString(), "name", "value")
            ),
            expiration = Clock.System.now() + 60.seconds,
            scheme = ConstantIndex.AtomicAttribute2023,
            subjectPublicKey = Client().randomKeyAdapter.publicKey,
            userInfo = OidcUserInfoExtended(OidcUserInfo("subject")),
        ),
        timePeriod,
    ).getOrThrow().statusListIndex
}
