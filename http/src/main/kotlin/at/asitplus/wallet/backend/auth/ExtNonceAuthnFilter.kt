package at.asitplus.wallet.backend.auth

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

    private val log = LoggerFactory.getLogger(this.javaClass)

    init {
        setRequiresAuthenticationRequestMatcher(RequestHeaderRequestMatcher("X-Auth-ExtNonce"))
    }

    override fun getPreAuthenticatedPrincipal(request: HttpServletRequest): Any? {
        val headerValue = request.getHeader("X-Auth-ExtNonce") ?: return null
        log.debug("Reading nonce '{}'", headerValue)
        return ExtNonceAuthnToken(headerValue)
    }

}
