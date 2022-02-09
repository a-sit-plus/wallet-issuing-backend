package at.asitplus.wallet.backend.auth

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * Is created by [NonceAuthenticationProvider], which exchanges nonce from [NonceAuthenticationToken].
 */
class AuthenticatedBpkToken(private val bpk: String) :
    AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("PUPIL"))) {

    override fun isAuthenticated(): Boolean {
        return true
    }

    override fun getCredentials(): Any? {
        return null
    }

    override fun getPrincipal(): Any {
        return bpk
    }

}