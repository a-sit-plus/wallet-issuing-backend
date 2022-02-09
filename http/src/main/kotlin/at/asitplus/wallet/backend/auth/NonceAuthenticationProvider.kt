package at.asitplus.wallet.backend.auth

import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Component

/**
 * Converts a [NonceAuthenticationToken] into a [AuthenticatedBpkToken] by exchanging the nonce with a bPK
 */
@Component
class NonceAuthenticationProvider : AuthenticationProvider {
    
    override fun authenticate(authentication: Authentication?): Authentication {
        if (authentication !is PreAuthenticatedAuthenticationToken)
            throw BadCredentialsException("not supported")
        val principal = authentication.principal
        if (principal !is NonceAuthenticationToken)
            throw BadCredentialsException("not supported")
        // TODO exchange nonce for bpk
        if (principal.nonce.length == 64) {
            return AuthenticatedBpkToken("bpk")
        }
        throw BadCredentialsException("Error")
    }

    override fun supports(authentication: Class<*>): Boolean {
        return PreAuthenticatedAuthenticationToken::class.java.isAssignableFrom(authentication)
    }

}