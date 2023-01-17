package at.asitplus.wallet.backend.service

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.time.Duration.Companion.seconds


interface ChallengeService {

    /**
     * Generate a new random `challenge`, stored for later verification.
     */
    fun generate(): ByteArray

    /**
     * Verify that the `challenge` is still valid.
     */
    fun verify(challenge: ByteArray): Boolean

    /**
     * Remove the `challenge` from the list of still-valid ones.
     */
    fun remove(challenge: ByteArray): Boolean

    /**
     * Verify that the `challenge` is still valid, and remove it.
     */
    fun verifyAndRemove(challenge: ByteArray): Boolean

}

class SimpleChallengeService(
    private val challengeLength: Int = 32,
    private val lifetimeSeconds: Int = 60,
) : ChallengeService {

    private val list = ConcurrentLinkedDeque<Entry>()

    override fun generate(): ByteArray {
        removeExpiredChallenges()
        return Entry(SecureRandom.nextBytes(challengeLength), Clock.System.now()).also { list += it }.challenge
    }

    override fun verify(challenge: ByteArray): Boolean {
        removeExpiredChallenges()
        return list.any { it.challenge.contentEquals(challenge) }
    }

    override fun remove(challenge: ByteArray): Boolean {
        return list.removeIf { it.challenge.contentEquals(challenge) }
    }

    override fun verifyAndRemove(challenge: ByteArray): Boolean {
        removeExpiredChallenges()
        return list.removeIf { it.challenge.contentEquals(challenge) }
    }

    private fun removeExpiredChallenges() {
        list.removeAll {
            it.creation < Clock.System.now() - lifetimeSeconds.seconds
        }
    }

    data class Entry(
        val challenge: ByteArray,
        val creation: Instant,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Entry

            if (!challenge.contentEquals(other.challenge)) return false
            if (creation != other.creation) return false

            return true
        }

        override fun hashCode(): Int {
            var result = challenge.contentHashCode()
            result = 31 * result + creation.hashCode()
            return result
        }
    }
}