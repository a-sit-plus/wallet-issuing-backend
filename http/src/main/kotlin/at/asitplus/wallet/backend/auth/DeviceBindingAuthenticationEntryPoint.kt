package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.lib.encodeBase64
import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.random.Random

/**
 * Sends a challenge to the client, since it is not authenticated. Response to this challenge will be picked up by [DeviceBindingAuthnFilter], which creates a [DeviceBindingAuthenticationToken].
 */
@Component
class DeviceBindingAuthenticationEntryPoint : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.addHeader("WWW-Authenticate", "Challenge ${Random.Default.nextBytes(32).encodeBase64()}")
        response.sendError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.reasonPhrase)
    }

}