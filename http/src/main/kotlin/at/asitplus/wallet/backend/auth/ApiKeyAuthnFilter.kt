package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.auth.WebSecurityConstants.X_API_KEY
import io.github.aakira.napier.Napier
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher
import jakarta.servlet.http.HttpServletRequest

/**
 * Reads an API Key from the HTTP header [X_API_KEY], creates a [ApiKeyAuthnToken].
 * Forwards via [AuthenticationManager] to [ApiKeyAuthnProvider].
 */
class ApiKeyAuthnFilter : RequestHeaderAuthenticationFilter() {

    init {
        setRequiresAuthenticationRequestMatcher(RequestHeaderRequestMatcher(X_API_KEY))
    }

    override fun getPreAuthenticatedPrincipal(request: HttpServletRequest): Any? {
        val headerValue = request.getHeader(X_API_KEY) ?: return null
        Napier.v("Reading apiKey '$headerValue'")
        return ApiKeyAuthnToken(headerValue)
    }

}
