package at.asitplus.wallet.backend.auth

import io.github.aakira.napier.Napier
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Component

/**
 * Authenticates user by reading API Key from an [ApiKeyAuthnToken],
 * by passing information to [ApiKeyAuthnService.validate].
 */
@Component
class ApiKeyAuthnProvider(
    private val apiKeyAuthnService: ApiKeyAuthnService
) : AuthenticationProvider {


    @Throws(BadCredentialsException::class)
    override fun authenticate(authentication: Authentication?): Authentication {
        if (authentication !is PreAuthenticatedAuthenticationToken)
            throw BadCredentialsException("not supported")
        val principal = authentication.principal
        if (principal !is ApiKeyAuthnToken)
            throw BadCredentialsException("not supported")
        val credentials = principal.credentials
        if (credentials !is String)
            throw BadCredentialsException("not supported")
        val username = apiKeyAuthnService.validate(credentials)
            ?: throw BadCredentialsException("Error")
                .also {
                    Napier.w("Could not validate credentials")
                    Napier.v("credentials: $credentials")
                }
        Napier.i("Exchanged apiKey for user")
        Napier.v("apiKey '$credentials', user '$username'")
        return ApiKeyAuthnToken(credentials, username)
    }

    override fun supports(authentication: Class<*>): Boolean {
        return PreAuthenticatedAuthenticationToken::class.java.isAssignableFrom(authentication)
    }

}