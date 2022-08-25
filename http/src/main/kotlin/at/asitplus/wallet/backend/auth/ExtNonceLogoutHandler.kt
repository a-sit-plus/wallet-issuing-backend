package at.asitplus.wallet.backend.auth

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.logout.LogoutHandler
import org.springframework.stereotype.Component
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Is called after a logout, i.e. at the end of the device binding process
 * to invalidate the `nonce` used during authentication,
 * by calling [ExtNonceAuthnService.invalidateNonce].
 */
@Component
class ExtNonceLogoutHandler(
    private val extNonceAuthnService: ExtNonceAuthnService
) : LogoutHandler {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun logout(request: HttpServletRequest?, response: HttpServletResponse?, authentication: Authentication?) {
        if (authentication !is ExtNonceAuthnToken)
            return
        val credentials = authentication.credentials
        if (credentials !is String)
            return
        log.info("Invalidating nonce '{}'", credentials)
        runBlocking { extNonceAuthnService.invalidateNonce(credentials) }
    }

}