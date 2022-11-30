package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.auth.WebSecurityConstants.X_AUTH_EXT_NONCE
import io.github.aakira.napier.Napier
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher
import javax.servlet.http.HttpServletRequest

/**
 * Reads the nonce from the HTTP header, creates a [ExtNonceAuthnToken].
 * Forwards via [AuthenticationManager] to [ExtNonceAuthnProvider].
 */
class ExtNonceAuthnFilter : RequestHeaderAuthenticationFilter() {


    init {
        setRequiresAuthenticationRequestMatcher(RequestHeaderRequestMatcher(X_AUTH_EXT_NONCE))
    }

    override fun getPreAuthenticatedPrincipal(request: HttpServletRequest): Any? {
        val headerValue = request.getHeader(X_AUTH_EXT_NONCE) ?: return null
        Napier.v("Reading nonce '$headerValue'")
        return ExtNonceAuthnToken(headerValue)
    }

}
