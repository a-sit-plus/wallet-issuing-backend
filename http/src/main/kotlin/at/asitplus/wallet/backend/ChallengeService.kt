package at.asitplus.wallet.backend

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds


interface ChallengeService {

    fun generate(): ByteArray

    fun verifyAndRemove(challenge: ByteArray): Boolean

}

class SimpleChallengeService(
    private val challengeLength: Int = 32,
    private val lifetimeSeconds: Int = 60,
) : ChallengeService {

    private val list = mutableListOf<Entry>()

    override fun generate() =
        Entry(Random.nextBytes(challengeLength), Clock.System.now()).also { list += it }.challenge

    override fun verifyAndRemove(challenge: ByteArray): Boolean {
        list.removeAll {
            it.creation < Clock.System.now() - lifetimeSeconds.seconds
        }
        return list.removeIf { it.challenge.contentEquals(challenge) }
    }

    data class Entry(
        val challenge: ByteArray,
        val creation: Instant,
    )
}