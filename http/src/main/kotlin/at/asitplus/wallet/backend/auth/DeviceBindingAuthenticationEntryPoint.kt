package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.ChallengeService
import at.asitplus.wallet.lib.encodeBase64
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Sends a challenge to the client, since it is not authenticated. Response to this challenge will be picked up by [DeviceBindingAuthnFilter], which creates a [DeviceBindingAuthenticationToken].
 */
@Component
class DeviceBindingAuthenticationEntryPoint(
    private val deviceBindingAuthnChallengeService: ChallengeService
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        val encodedChallenge = deviceBindingAuthnChallengeService.generate().encodeBase64()
        response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Challenge $encodedChallenge")
        response.sendError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.reasonPhrase)
    }

}