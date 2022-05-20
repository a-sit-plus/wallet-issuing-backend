package at.asitplus.wallet.backend

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent
import org.springframework.security.authentication.event.AuthenticationSuccessEvent
import org.springframework.stereotype.Component

/**
 * Logs authentication events: Success and failures
 */
@Component
class AuthenticationEvents {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @EventListener
    fun onSuccess(success: AuthenticationSuccessEvent?) {
        success?.let {
            log.info("Authentication success: {}", it)
        }
    }

    @EventListener
    fun onFailure(failure: AbstractAuthenticationFailureEvent?) {
        failure?.let {
            log.error("Authentication failure: {}", it)
        }
    }
}