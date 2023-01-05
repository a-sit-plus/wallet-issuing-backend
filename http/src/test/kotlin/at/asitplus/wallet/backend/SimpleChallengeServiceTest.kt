package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.service.SimpleChallengeService
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test

class SimpleChallengeServiceTest {

    private val timeoutSeconds = 1
    private val service = SimpleChallengeService(lifetimeSeconds = timeoutSeconds)

    @Test
    fun timeout() {
        val challenge = service.generate()
        Thread.sleep((timeoutSeconds + 1) * 1000L)

          service.verifyAndRemove(challenge) shouldBe false
    }

    @Test
    fun noTimeout() {
        val challenge = service.generate()

        service.verifyAndRemove(challenge) shouldBe true
    }

}