package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.auth.WebSecurityConstants.AUTHORITY_PUPIL
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

class ExtNonceAuthnToken : AbstractAuthenticationToken {

    private val credentials: String
    private val principal: String?

    /**
     * Called from [ExtNonceAuthnFilter].
     */
    constructor(nonce: String) : super(null) {
        this.credentials = nonce
        this.principal = null
        this.isAuthenticated = false
    }

    /**
     * Called from [ExtNonceAuthnProvider]
     * after successful authentication.
     */
    constructor(nonce: String, bpk: String) : super(listOf(SimpleGrantedAuthority(AUTHORITY_PUPIL))) {
        this.credentials = nonce
        this.principal = bpk
        this.isAuthenticated = true
    }

    override fun getCredentials(): Any {
        return credentials
    }

    override fun getPrincipal(): Any? {
        return principal
    }

    override fun toString(): String {
        return "ExtNonceAuthnToken(principal='$principal', credentials='$credentials')"
    }

}