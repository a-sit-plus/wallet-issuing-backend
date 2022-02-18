package at.asitplus.wallet.backend.auth

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter
import javax.servlet.http.HttpServletRequest

/**
 * Reads the nonce from the HTTP header, creates a [ExtNonceAuthnToken].
 * Forwards via [AuthenticationManager] to [ExtNonceAuthnProvider].
 */
class ExtNonceAuthnFilter : RequestHeaderAuthenticationFilter() {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun getPreAuthenticatedPrincipal(request: HttpServletRequest): Any? {
        val headerValue = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!headerValue.startsWith("Nonce ")) return null
        val nonce = headerValue.removePrefix("Nonce ")
        log.debug("Reading nonce '{}'", nonce)
        return ExtNonceAuthnToken(nonce)
    }

}
