package at.asitplus.wallet.backend.auth

import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher
import javax.servlet.http.HttpServletRequest

/**
 * Reads an API Key from the HTTP header `X-API-Key`, creates a [ApiKeyAuthnToken].
 * Forwards via [AuthenticationManager] to [ApiKeyAuthnProvider].
 */
class ApiKeyAuthnFilter : RequestHeaderAuthenticationFilter() {

    private val log = LoggerFactory.getLogger(this.javaClass)

    init {
        setRequiresAuthenticationRequestMatcher(RequestHeaderRequestMatcher("X-API-Key"))
    }

    override fun getPreAuthenticatedPrincipal(request: HttpServletRequest): Any? {
        val headerValue = request.getHeader("X-API-Key") ?: return null
        log.debug("Reading apiKey '{}'", headerValue)
        return ApiKeyAuthnToken(headerValue)
    }

}
