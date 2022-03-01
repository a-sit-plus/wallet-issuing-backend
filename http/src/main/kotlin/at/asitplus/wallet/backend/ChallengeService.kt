package at.asitplus.wallet.backend

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds


interface ChallengeService {

    fun generate(): ByteArray

    fun verify(challenge: ByteArray): Boolean

    fun remove(challenge: ByteArray): Boolean

    fun verifyAndRemove(challenge: ByteArray): Boolean

}

class SimpleChallengeService(
    private val challengeLength: Int = 32,
    private val lifetimeSeconds: Int = 60,
) : ChallengeService {

    private val list = mutableListOf<Entry>()

    override fun generate(): ByteArray {
        removeExpiredChallenges()
        return Entry(Random.nextBytes(challengeLength), Clock.System.now()).also { list += it }.challenge
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
    )
}