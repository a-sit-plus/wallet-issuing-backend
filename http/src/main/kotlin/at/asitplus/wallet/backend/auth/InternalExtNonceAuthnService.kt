package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.service.ChallengeService
import at.asitplus.wallet.lib.decodeBase16ToArray
import at.asitplus.wallet.lib.encodeBase16
import java.util.UUID

/**
 * Validates ext. nonce from [ExtNonceAuthnToken] internally,
 * i.e. the user needs to login on the Desktop browser (i.e. Debug deployments),
 * and scan a QR code from there to get a valid ext. nonce.
 */
class InternalExtNonceAuthnService(
    private val challengeService: ChallengeService
) : ExtNonceAuthnService {

    private val mapChallengeToBpk = mutableMapOf<String, String>()

    override suspend fun generateNonce(): ExtNonceAuthnService.NonceBpk {
        val challenge = challengeService.generate().encodeBase16()
        val bpk = UUID.randomUUID().toString()
        mapChallengeToBpk[challenge] = bpk
        return ExtNonceAuthnService.NonceBpk(challenge, bpk)
    }

    override suspend fun exchangeNonceForBpk(nonce: String): String? {
        if (nonce.decodeBase16ToArray()?.let { challengeService.verify(it) } == true) {
            return mapChallengeToBpk.remove(nonce)
        }
        return null
    }

    override suspend fun invalidateNonce(nonce: String): Boolean {
        return nonce.decodeBase16ToArray()?.let { challengeService.remove(it) } == true
                && mapChallengeToBpk.remove(nonce) != null
    }

}