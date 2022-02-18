package at.asitplus.wallet.backend.auth

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter
import javax.servlet.http.HttpServletRequest

/**
 * Reads the response from the HTTP header, creates a [DeviceBindingAuthnToken].
 * Forwards via [AuthenticationManager] to [DeviceBindingAuthnProvider].
 */
class DeviceBindingAuthnFilter : RequestHeaderAuthenticationFilter() {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun getPreAuthenticatedPrincipal(request: HttpServletRequest): Any? {
        val headerValue = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!headerValue.startsWith("Response ")) return null
        val stripped = headerValue.removePrefix("Response ")
        log.debug("Reading response '{}'", stripped)
        return DeviceBindingAuthnToken(stripped)
    }

}
