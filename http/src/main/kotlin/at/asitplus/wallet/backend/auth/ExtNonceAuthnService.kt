package at.asitplus.wallet.backend.auth

/**
 * Service to validate an ext. nonce provided from Wallet App in [ExtNonceAuthnToken].
 */
interface ExtNonceAuthnService {

    /**
     * Called in debug settings to generate a new `nonce`,
     * that clients can use to authenticate.
     */
    suspend fun generateNonce(): NonceBpk?

    /**
     * Called during authentication to verify the `nonce`
     * sent from a client.
     */
    suspend fun exchangeNonceForBpk(nonce: String): String?

    /**
     * Called after successful creation of a device binding
     * to invalidate the `nonce`, so that the client can not
     * use the same `nonce` to authenticate again.
     */
   suspend fun invalidateNonce(nonce: String): Boolean

    data class NonceBpk(
        val nonce: String,
        val bpk: String,
    )

}

