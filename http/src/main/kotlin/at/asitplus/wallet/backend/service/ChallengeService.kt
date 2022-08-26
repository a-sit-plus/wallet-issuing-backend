package at.asitplus.wallet.backend.service

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.util.concurrent.LinkedBlockingQueue
import javax.annotation.PreDestroy
import kotlin.concurrent.thread
import kotlin.random.Random
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
    private val clock: Clock
) : ChallengeService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val list = mutableListOf<Entry>()
    private val msgChannel = LinkedBlockingQueue<ChallengeMsg<*>>(1000)

    private sealed class ChallengeMsg<T> {
        protected val resp: LinkedBlockingQueue<T> = LinkedBlockingQueue(1)
        fun receive(): T = resp.take()

        fun respond(response: T) {
            resp.put(response)
        }
    }

    @PreDestroy
    private fun stop() {
        log.info("Shutting down ChallengeService")
        msgChannel.put(TerminateMsg)
    }

    private class GenerateMsg : ChallengeMsg<ByteArray>()
    private object TerminateMsg : ChallengeMsg<Unit>()
    private class VerifyMsg(val challenge: ByteArray) : ChallengeMsg<Boolean>()
    private class RemoveMsg(val challenge: ByteArray) : ChallengeMsg<Boolean>()
    private class VerifyAndRemoveMsg(val challenge: ByteArray) : ChallengeMsg<Boolean>()


    init {
        thread(isDaemon = true, name = "ChallengeServiceWorker") {
            while (true) {
                when (val msg = msgChannel.take()) {
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

                    TerminateMsg -> break
                }
            }
            log.info("ChallengeService terminated")
        }
    }


    private fun <R, T : ChallengeMsg<R>> process(msg: T): R {
        msgChannel.put(msg)
        return msg.receive()
    }

    override fun generate(): ByteArray = process(GenerateMsg())


    override fun verify(challenge: ByteArray): Boolean = process(VerifyMsg(challenge))

    override fun remove(challenge: ByteArray): Boolean = process(RemoveMsg(challenge))

    override fun verifyAndRemove(challenge: ByteArray): Boolean = process(VerifyAndRemoveMsg(challenge))

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