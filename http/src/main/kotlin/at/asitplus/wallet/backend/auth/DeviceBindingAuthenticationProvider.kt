package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.DeviceBindingResponseValidator
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Component

/**
 * Authenticates user by reading information from a [DeviceBindingAuthenticationToken],
 * by passing information to [DeviceBindingResponseValidator.validate].
 */
@Component
class DeviceBindingAuthenticationProvider(
    private val deviceBindingResponseValidator: DeviceBindingResponseValidator,
) : AuthenticationProvider {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun authenticate(authentication: Authentication?): Authentication {
        if (authentication !is PreAuthenticatedAuthenticationToken)
            throw BadCredentialsException("not supported")
        val principal = authentication.principal
        if (principal !is DeviceBindingAuthenticationToken)
            throw BadCredentialsException("not supported")
        val credentials = principal.credentials
        log.info("Trying to authenticate '{}'", credentials)
        if (credentials !is String)
            throw BadCredentialsException("not supported")
        val result = deviceBindingResponseValidator.validate(credentials)
            ?: throw BadCredentialsException("bpk not found")
        return DeviceBindingAuthenticationToken(credentials, result.bpk, result.certificate)
    }

    override fun supports(authentication: Class<*>): Boolean {
        return PreAuthenticatedAuthenticationToken::class.java.isAssignableFrom(authentication)
    }

}