package at.asitplus.wallet.backend

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random


interface ChallengeService {

    fun generate(): ByteArray

    fun verifyAndRemove(challenge: ByteArray): Boolean
}

class SimpleChallengeService : ChallengeService {

    private val list = mutableListOf<Entry>()

    override fun generate() = Entry(Random.nextBytes(32), Clock.System.now()).also { list += it }.challenge

    override fun verifyAndRemove(challenge: ByteArray) =
        list.removeIf { it.challenge.contentEquals(challenge) && it.creation < Clock.System.now() }

    data class Entry(
        val challenge: ByteArray,
        val creation: Instant,
    )
}