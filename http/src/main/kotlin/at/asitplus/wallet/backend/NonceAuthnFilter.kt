package at.asitplus.wallet.backend

import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter
import javax.servlet.http.HttpServletRequest

/**
 * Reads the nonce from the HTTP header, creates a [NonceAuthenticationToken].
 * Forwards via [AuthenticationManager] to [NonceAuthenticationProvider].
 */
class NonceAuthnFilter : RequestHeaderAuthenticationFilter() {

    override fun getPreAuthenticatedPrincipal(request: HttpServletRequest?): Any? {
        val headerValue = request?.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        val nonce = headerValue.replace("Nonce ", "")
        return NonceAuthenticationToken(nonce)
    }

}
