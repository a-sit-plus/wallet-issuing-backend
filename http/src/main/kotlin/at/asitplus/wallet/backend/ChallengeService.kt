package at.asitplus.wallet.backend

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class ChallengeService {

    private val list = mutableListOf<Entry>()

    fun generate() =
        Entry(Random.Default.nextBytes(32), Clock.System.now()).also { list += it }.challenge

    fun verifyAndRemove(challenge: ByteArray) =
        list.removeIf { it.challenge.contentEquals(challenge) && it.creation < Clock.System.now() }

    data class Entry(
        val challenge: ByteArray,
        val creation: Instant,
    )
}