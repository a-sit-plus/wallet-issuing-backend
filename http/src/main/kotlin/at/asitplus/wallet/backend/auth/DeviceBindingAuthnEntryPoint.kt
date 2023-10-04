package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.service.ChallengeService
import io.github.aakira.napier.Napier
import io.matthewnelson.component.base64.encodeBase64
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * Sends a challenge to the client, since it is not authenticated.
 * Response to this challenge will be picked up by [DeviceBindingAuthnFilter], which creates a [DeviceBindingAuthnToken].
 */
class DeviceBindingAuthnEntryPoint(
    private val deviceBindingAuthnChallengeService: ChallengeService
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {

        val encodedChallenge = deviceBindingAuthnChallengeService.generate().encodeToString(Base64())
        response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Challenge $encodedChallenge")
        Napier.v("Sending challenge '$encodedChallenge'")
        response.sendError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.reasonPhrase)
    }

}
