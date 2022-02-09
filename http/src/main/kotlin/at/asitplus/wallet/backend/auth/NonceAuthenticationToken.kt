package at.asitplus.wallet.backend.auth

import org.springframework.security.authentication.AbstractAuthenticationToken

/**
 * Is created from the HTTP header value in [NonceAuthnFilter].
 * Gets exchanged into an [AuthenticatedBpkToken] in [NonceAuthenticationProvider].
 */
class NonceAuthenticationToken(val nonce: String) : AbstractAuthenticationToken(null) {

    override fun getCredentials(): Any {
        return nonce
    }

    override fun getPrincipal(): Any? {
        return null
    }

}