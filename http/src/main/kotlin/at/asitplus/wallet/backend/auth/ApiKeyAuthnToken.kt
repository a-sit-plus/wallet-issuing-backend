package at.asitplus.wallet.backend.auth

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * Token to transport the API Key and possibly user name from client's authn
 */
class ApiKeyAuthnToken : AbstractAuthenticationToken {

    private val credentials: String
    private val principal: String?

    /**
     * Called from [ApiKeyAuthnFilter].
     */
    constructor(apiKey: String) : super(null) {
        this.credentials = apiKey
        this.principal = null
        this.isAuthenticated = false
    }

    /**
     * Called from [ApiKeyAuthnProvider]
     * after successful authentication.
     */
    constructor(apiKey: String, username: String) : super(listOf(SimpleGrantedAuthority("REVOCATION"))) {
        this.credentials = apiKey
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