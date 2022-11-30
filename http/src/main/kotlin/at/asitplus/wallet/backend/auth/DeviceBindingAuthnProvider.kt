package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.DeviceBindingAuthnService
import io.github.aakira.napier.Napier
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Component

/**
 * Authenticates user by reading information from a [DeviceBindingAuthnToken],
 * by passing information to [DeviceBindingAuthnService.validate].
 */
@Component
class DeviceBindingAuthnProvider(
    private val deviceBindingAuthnService: DeviceBindingAuthnService,
) : AuthenticationProvider {


    @Throws(BadCredentialsException::class)
    override fun authenticate(authentication: Authentication?): Authentication {
        if (authentication !is PreAuthenticatedAuthenticationToken)
            throw BadCredentialsException("not supported")
        val principal = authentication.principal
        if (principal !is DeviceBindingAuthnToken)
            throw BadCredentialsException("not supported")
        val credentials = principal.credentials
        Napier.i("Trying to authenticate user")
        Napier.v("user: $credentials")
        if (credentials !is String)
            throw BadCredentialsException("not supported")
        val result = deviceBindingAuthnService.validate(credentials)
            ?: throw BadCredentialsException("bpk not found")
                .also {
                    Napier.w("Could not validate credentials")
                    Napier.v("credentials: $credentials")
                }
        return DeviceBindingAuthnToken(credentials, result.bpk, result.certificate)
    }

    override fun supports(authentication: Class<*>): Boolean {
        return PreAuthenticatedAuthenticationToken::class.java.isAssignableFrom(authentication)
    }

}