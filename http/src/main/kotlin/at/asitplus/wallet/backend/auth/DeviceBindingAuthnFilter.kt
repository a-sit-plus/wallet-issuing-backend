package at.asitplus.wallet.backend.auth

import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter
import javax.servlet.http.HttpServletRequest

/**
 * Reads the response from the HTTP header, creates a [DeviceBindingAuthenticationToken].
 * Forwards via [AuthenticationManager] to [DeviceBindingAuthenticationProvider].
 */
class DeviceBindingAuthnFilter : RequestHeaderAuthenticationFilter() {

    override fun getPreAuthenticatedPrincipal(request: HttpServletRequest): Any? {
        val headerValue = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!headerValue.startsWith("Response ")) return null
        val stripped = headerValue.removePrefix("Response ")
        return DeviceBindingAuthenticationToken(stripped)
    }

}
