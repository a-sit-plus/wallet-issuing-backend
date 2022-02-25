package at.asitplus.wallet.backend

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimpleChallengeServiceTest {

    private val timeoutSeconds = 1
    private val service = SimpleChallengeService(lifetimeSeconds = timeoutSeconds)

    @Test
    fun timeout() {
        val challenge = service.generate()
        Thread.sleep((timeoutSeconds + 1) * 1000L)

        assertFalse(service.verifyAndRemove(challenge))
    }

    @Test
    fun noTimeout() {
        val challenge = service.generate()

        assertTrue(service.verifyAndRemove(challenge))
    }

}