package at.asitplus.wallet.backend.auth

import io.github.aakira.napier.Napier
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Component

/**
 * Authenticates user by reading information from a [ExtNonceAuthnToken],
 * by passing information to [ExtNonceAuthnService.exchangeNonceForBpk].
 */
@Component
class ExtNonceAuthnProvider(
    private val extNonceAuthnService: ExtNonceAuthnService
) : AuthenticationProvider {


    @Throws(BadCredentialsException::class)
    override fun authenticate(authentication: Authentication?): Authentication {
        if (authentication !is PreAuthenticatedAuthenticationToken)
            throw BadCredentialsException("not supported")
        val principal = authentication.principal
        if (principal !is ExtNonceAuthnToken)
            throw BadCredentialsException("not supported")
        val credentials = principal.credentials
        if (credentials !is String)
            throw BadCredentialsException("not supported")
        val bpk = extNonceAuthnService.exchangeNonceForBpk(credentials)
            ?: throw BadCredentialsException("Error")
                .also {
                    Napier.w("Could not validate credentials")
                    Napier.v("credentials: $credentials")
                }
        Napier.v("Exchanged nonce '$credentials' for bpk '$bpk'")
        return ExtNonceAuthnToken(credentials, bpk)
    }

    override fun supports(authentication: Class<*>): Boolean {
        return PreAuthenticatedAuthenticationToken::class.java.isAssignableFrom(authentication)
    }

}