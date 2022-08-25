package at.asitplus.wallet.backend.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds


interface ChallengeService {

    /**
     * Generate a new random `challenge`, stored for later verification.
     */
   suspend  fun generate(): ByteArray

    /**
     * Verify that the `challenge` is still valid.
     */
    suspend fun verify(challenge: ByteArray): Boolean

    /**
     * Remove the `challenge` from the list of still-valid ones.
     */
 suspend   fun remove(challenge: ByteArray): Boolean

    /**
     * Verify that the `challenge` is still valid, and remove it.
     */
   suspend fun verifyAndRemove(challenge: ByteArray): Boolean

}

class SimpleChallengeService(
    private val challengeLength: Int = 32,
    private val lifetimeSeconds: Int = 60,
    private val clock:Clock
) : ChallengeService {
private val lock = Mutex()
    private val list = mutableListOf<Entry>()

    override suspend fun generate(): ByteArray {
        removeExpiredChallenges()
        return Entry(Random.nextBytes(challengeLength), clock.now()).also { item ->lock.withLock { list += item} }.challenge
    }

    override suspend fun verify(challenge: ByteArray): Boolean {
        removeExpiredChallenges()
        return list.any { it.challenge.contentEquals(challenge) }
    }

    override suspend fun remove(challenge: ByteArray): Boolean {
        return lock.withLock { list.removeIf { it.challenge.contentEquals(challenge) }}
    }

    override suspend fun verifyAndRemove(challenge: ByteArray): Boolean {
        lock.withLock { removeExpiredChallenges()
        return list.removeIf { it.challenge.contentEquals(challenge) }}
    }

    private suspend  fun removeExpiredChallenges() {
        list.removeAll {
            it.creation < clock.now() - lifetimeSeconds.seconds
        }
    }

    data class Entry(
        val challenge: ByteArray,
        val creation: Instant,
    )
}