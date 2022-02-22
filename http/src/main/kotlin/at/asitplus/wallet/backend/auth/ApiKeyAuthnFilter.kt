package at.asitplus.wallet.backend.auth

import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter
import javax.servlet.http.HttpServletRequest

/**
 * Reads the nonce from the HTTP header, creates a [ApiKeyAuthnToken].
 * Forwards via [AuthenticationManager] to [ApiKeyAuthnProvider].
 */
class ApiKeyAuthnFilter : RequestHeaderAuthenticationFilter() {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun getPreAuthenticatedPrincipal(request: HttpServletRequest): Any? {
        val headerValue = request.getHeader("X-API-Key") ?: return null
        log.debug("Reading apiKey '{}'", headerValue)
        return ApiKeyAuthnToken(headerValue)
    }

}
