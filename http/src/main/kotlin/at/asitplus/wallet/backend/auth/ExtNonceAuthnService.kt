package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.ChallengeService
import at.asitplus.wallet.lib.decodeBase16ToArray
import at.asitplus.wallet.lib.encodeBase16
import java.util.UUID

interface ExtNonceAuthnService {

    /**
     * Called in debug settings to generate a new `nonce`,
     * that clients can use to authenticate.
     */
    fun generateNonce(): String?

    /**
     * Called during authentication to verify the `nonce`
     * sent from a client.
     */
    fun exchangeNonceForBpk(nonce: String): String?

    /**
     * Called after successful creation of a device binding
     * to invalidate the `nonce`, so that the client can not
     * use the same `nonce` to authenticate again.
     */
    fun invalidateNonce(nonce: String): Boolean

}

class NoopExtNonceAuthnService : ExtNonceAuthnService {

    override fun generateNonce(): String? {
        return null
    }

    override fun exchangeNonceForBpk(nonce: String): String {
        return "bpk"
    }

    override fun invalidateNonce(nonce: String): Boolean {
        return true
    }

}

class DebugExtNonceAuthnService(
    private val challengeService: ChallengeService
) : ExtNonceAuthnService {

    override fun generateNonce(): String {
        return challengeService.generate().encodeBase16()
    }

    override fun exchangeNonceForBpk(nonce: String): String? {
        if (nonce.decodeBase16ToArray()?.let { challengeService.verify(it) } == true) {
            return UUID.randomUUID().toString()
        }
        return null
    }

    override fun invalidateNonce(nonce: String): Boolean {
        return nonce.decodeBase16ToArray()?.let { challengeService.remove(it) } == true
    }

}