package at.asitplus.wallet.backend

import org.springframework.context.event.EventListener
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent
import org.springframework.security.authentication.event.AuthenticationSuccessEvent
import org.springframework.stereotype.Component
import io.github.aakira.napier.Napier

/**
 * Logs authentication events: Success and failures
 */
@Component
class AuthenticationEvents {

    @EventListener
    fun onSuccess(success: AuthenticationSuccessEvent?) {
        success?.let {
            Napier.i("Authentication success: $it")
        }
    }

    @EventListener
    fun onFailure(failure: AbstractAuthenticationFailureEvent?) {
        failure?.let {
            Napier.e("Authentication failure: $it")
        }
    }
}