package at.asitplus.wallet.backend.auth

import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Component

/**
 *
 * Authenticates user by reading information from a [NonceAuthenticationToken],
 * by passing information to [NonceToBpkService.validate].
 */
@Component
class NonceAuthenticationProvider(
    private val nonceToBpkService: NonceToBpkService
) : AuthenticationProvider {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun authenticate(authentication: Authentication?): Authentication {
        if (authentication !is PreAuthenticatedAuthenticationToken)
            throw BadCredentialsException("not supported")
        val principal = authentication.principal
        if (principal !is NonceAuthenticationToken)
            throw BadCredentialsException("not supported")
        val credentials = principal.credentials
        if (credentials !is String)
            throw BadCredentialsException("not supported")
        val bpk = nonceToBpkService.validate(credentials)
            ?: throw BadCredentialsException("Error")
        log.info("Exchanged nonce '{}' for bpk '{}'", credentials, bpk)
        return NonceAuthenticationToken(credentials, bpk)
    }

    override fun supports(authentication: Class<*>): Boolean {
        return PreAuthenticatedAuthenticationToken::class.java.isAssignableFrom(authentication)
    }

}