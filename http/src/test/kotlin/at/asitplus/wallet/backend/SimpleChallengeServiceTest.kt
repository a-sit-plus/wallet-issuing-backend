package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.service.SimpleChallengeService
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test

class SimpleChallengeServiceTest {

    private val timeoutSeconds = 1
    private val service = SimpleChallengeService(
        lifetimeSeconds = timeoutSeconds,
        clock = Clock.System
    )

    @Test
    fun timeout() {
        val challenge = runBlocking {  service.generate()}
        Thread.sleep((timeoutSeconds + 1) * 1000L)

        runBlocking {  service.verifyAndRemove(challenge) shouldBe false}
    }

    @Test
    fun noTimeout() {
        val challenge = runBlocking {  service.generate()}

        runBlocking {  service.verifyAndRemove(challenge) shouldBe true}
    }

}