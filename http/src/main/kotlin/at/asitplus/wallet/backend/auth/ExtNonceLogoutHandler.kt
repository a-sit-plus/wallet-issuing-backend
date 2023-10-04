package at.asitplus.wallet.backend.auth

import io.github.aakira.napier.Napier
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.logout.LogoutHandler
import org.springframework.stereotype.Component
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * Is called after a logout, i.e. at the end of the device binding process
 * to invalidate the `nonce` used during authentication,
 * by calling [ExtNonceAuthnService.invalidateNonce].
 */
@Component
class ExtNonceLogoutHandler(
    private val extNonceAuthnService: ExtNonceAuthnService
) : LogoutHandler {


    override fun logout(request: HttpServletRequest?, response: HttpServletResponse?, authentication: Authentication?) {
        if (authentication !is ExtNonceAuthnToken)
            return
        val credentials = authentication.credentials
        if (credentials !is String)
            return
        Napier.i("Invalidating nonce")
        Napier.v("nonce: $credentials")
        extNonceAuthnService.invalidateNonce(credentials)
    }

}