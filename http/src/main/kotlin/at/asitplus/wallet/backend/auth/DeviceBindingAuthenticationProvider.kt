package at.asitplus.wallet.backend.auth

import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Component

/**
 * Converts a [DeviceBindingAuthenticationToken] into a [AuthenticatedDeviceBindingToken] by validating the response to the challenge.
 */
@Component
class DeviceBindingAuthenticationProvider : AuthenticationProvider {

    override fun authenticate(authentication: Authentication?): Authentication {
        if (authentication !is PreAuthenticatedAuthenticationToken)
            throw BadCredentialsException("not supported")
        val principal = authentication.principal
        if (principal !is DeviceBindingAuthenticationToken)
            throw BadCredentialsException("not supported")
        // TODO validate response to challenge, lookup public key in database somehow, read bpk
        if (principal.nonce.length > 32) {
            return AuthenticatedDeviceBindingToken("bpk")
        }
        throw BadCredentialsException("Error")
    }

    override fun supports(authentication: Class<*>): Boolean {
        return PreAuthenticatedAuthenticationToken::class.java.isAssignableFrom(authentication)
    }

}