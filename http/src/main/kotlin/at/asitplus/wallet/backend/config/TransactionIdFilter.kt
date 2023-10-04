package at.asitplus.wallet.backend.config

import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.*
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest

const val MDC_KEY = "txID"

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestResponseLoggingFilter : Filter {
    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain
    ) {
        val newTransactionId = loadOrCreateTransactionId(request)
        MDC.put(MDC_KEY, newTransactionId)
        storeTransactionId(request, newTransactionId)
        chain.doFilter(request, response)
    }

    /**
     * If the current HTTP session is known and already has a transaction ID set,
     * use this one, otherwise create a new one.
     * This is helpful to keep the same transaction ID across multiple requests of a client.
     */
    private fun loadOrCreateTransactionId(request: ServletRequest): String {
        val newTransactionId = UUID.randomUUID().toString()
        if (request is HttpServletRequest) {
            kotlin.runCatching {
                return request.session.getAttribute(MDC_KEY)?.toString() ?: newTransactionId
            }
        }
        return newTransactionId
    }

    /**
     * Stores the transaction ID in the HTTP session, for retrieval in subsequent requests
     */
    private fun storeTransactionId(request: ServletRequest, newTransactionId: String) {
        if (request is HttpServletRequest) {
            kotlin.runCatching {
                request.session.setAttribute(MDC_KEY, newTransactionId)
            }
        }
    }

    /**
     * Be sure to remove the transaction ID again, if this thread may be reused otherwise
     */
    override fun destroy() {
        MDC.remove(MDC_KEY)
        super.destroy()
    }
}