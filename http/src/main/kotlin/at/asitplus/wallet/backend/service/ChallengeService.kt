package at.asitplus.wallet.backend.service

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds


interface ChallengeService {

    /**
     * Generate a new random `challenge`, stored for later verification.
     */
    suspend fun generate(): ByteArray

    /**
     * Verify that the `challenge` is still valid.
     */
    suspend fun verify(challenge: ByteArray): Boolean

    /**
     * Remove the `challenge` from the list of still-valid ones.
     */
    suspend fun remove(challenge: ByteArray): Boolean

    /**
     * Verify that the `challenge` is still valid, and remove it.
     */
    suspend fun verifyAndRemove(challenge: ByteArray): Boolean

}

class SimpleChallengeService(
    private val challengeLength: Int = 32,
    private val lifetimeSeconds: Int = 60,
    private val clock: Clock
) : ChallengeService {
    private val msgChannel = Channel<ChallengeMsg<*>>(1000)

    private sealed class ChallengeMsg<T> {
        protected val resp: Channel<T> = Channel(1)
        suspend fun receive(): T = resp.receive()

        suspend fun respond(response: T) {
            resp.send(response)
        }
    }

    private class GenerateMsg : ChallengeMsg<ByteArray>()
    private class VerifyMsg(val challenge: ByteArray) : ChallengeMsg<Boolean>()
    private class RemoveMsg(val challenge: ByteArray) : ChallengeMsg<Boolean>()
    private class VerifyAndRemoveMsg(val challenge: ByteArray) : ChallengeMsg<Boolean>()


    init {
        GlobalScope.launch {
            while (!msgChannel.isClosedForReceive) {
                when (val msg = msgChannel.receive()) {
                    is GenerateMsg -> {
                        removeExpiredChallenges()
                        msg.respond(
                            Entry(
                                Random.nextBytes(challengeLength),
                                clock.now()
                            ).also { item -> list += item }.challenge
                        )
                    }

                    is VerifyMsg -> {
                        removeExpiredChallenges()
                        msg.respond(list.any { it.challenge.contentEquals(msg.challenge) })
                    }

                    is RemoveMsg -> {
                        msg.respond(list.removeIf { it.challenge.contentEquals(msg.challenge) })
                    }

                    is VerifyAndRemoveMsg -> {
                        removeExpiredChallenges()
                        msg.respond(list.removeIf { it.challenge.contentEquals(msg.challenge) })
                    }
                }
            }
        }
    }

    private val list = mutableListOf<Entry>()

    private suspend inline fun <reified R, T : ChallengeMsg<R>> process(msg: T): R {
        msgChannel.send(msg)
        return msg.receive()
    }

    override suspend fun generate(): ByteArray = process(GenerateMsg())


    override suspend fun verify(challenge: ByteArray): Boolean = process(VerifyMsg(challenge))

    override suspend fun remove(challenge: ByteArray): Boolean = process(RemoveMsg(challenge))

    override suspend fun verifyAndRemove(challenge: ByteArray): Boolean = process(VerifyAndRemoveMsg(challenge))

    private fun removeExpiredChallenges() {
        list.removeAll {
            it.creation < clock.now() - lifetimeSeconds.seconds
        }
    }

    data class Entry(
        val challenge: ByteArray,
        val creation: Instant,
    )
}