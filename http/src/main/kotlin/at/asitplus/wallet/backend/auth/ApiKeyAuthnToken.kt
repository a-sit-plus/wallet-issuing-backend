package at.asitplus.wallet.backend.auth

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

class ApiKeyAuthnToken : AbstractAuthenticationToken {

    private val credentials: String
    private val principal: String?

    /**
     * Called from [ApiKeyAuthnFilter].
     */
    constructor(nonce: String) : super(null) {
        this.credentials = nonce
        this.principal = null
        this.isAuthenticated = false
    }

    /**
     * Called from [ApiKeyAuthnProvider]
     * after successful authentication.
     */
    constructor(nonce: String, username: String) : super(listOf(SimpleGrantedAuthority("REVOCATION"))) {
        this.credentials = nonce
        this.principal = username
        this.isAuthenticated = true
    }

    override fun getCredentials(): Any {
        return credentials
    }

    override fun getPrincipal(): Any? {
        return principal
    }

}