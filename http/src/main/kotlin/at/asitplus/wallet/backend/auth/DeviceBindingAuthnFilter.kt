package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.auth.WebSecurityConstants.PREFIX_RESPONSE
import io.github.aakira.napier.Napier
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher
import javax.servlet.http.HttpServletRequest

/**
 * Reads the response from the HTTP header `Authorization`, creates a [DeviceBindingAuthnToken].
 * Forwards via [AuthenticationManager] to [DeviceBindingAuthnProvider].
 */
class DeviceBindingAuthnFilter : RequestHeaderAuthenticationFilter() {

    init {
        setRequiresAuthenticationRequestMatcher(RequestHeaderRequestMatcher(HttpHeaders.AUTHORIZATION))
    }

    override fun getPreAuthenticatedPrincipal(request: HttpServletRequest): Any? {
        val headerValue = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!headerValue.startsWith(PREFIX_RESPONSE)) return null
        val stripped = headerValue.removePrefix(PREFIX_RESPONSE).trim()
        Napier.d("Reading response")
        Napier.v("Reading response '$stripped'")
        return DeviceBindingAuthnToken(stripped)
    }

}
