package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.ChallengeService
import at.asitplus.wallet.lib.decodeBase16ToArray
import at.asitplus.wallet.lib.encodeBase16
import java.util.UUID

interface ExtNonceAuthnService {

    fun validate(nonce: String): String?

    fun generateNonce(): String?

}

class NoopExtNonceAuthnService : ExtNonceAuthnService {

    override fun generateNonce(): String? {
        return null
    }

    override fun validate(nonce: String): String {
        return "bpk"
    }

}

class DebugExtNonceAuthnService(
    private val challengeService: ChallengeService
) : ExtNonceAuthnService {

    override fun generateNonce(): String {
        return challengeService.generate().encodeBase16()
    }

    override fun validate(nonce: String): String? {
        if (nonce.decodeBase16ToArray()?.let { challengeService.verifyAndRemove(it) } == true) {
            return UUID.randomUUID().toString()
        }
        return null
    }

}