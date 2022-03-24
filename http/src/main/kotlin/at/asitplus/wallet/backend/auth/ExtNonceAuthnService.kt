package at.asitplus.wallet.backend.auth

interface ExtNonceAuthnService {

    /**
     * Called in debug settings to generate a new `nonce`,
     * that clients can use to authenticate.
     */
    fun generateNonce(): NonceBpk?

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

    data class NonceBpk(
        val nonce: String,
        val bpk: String,
    )

}

