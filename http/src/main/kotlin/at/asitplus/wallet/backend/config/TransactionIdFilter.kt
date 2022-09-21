package at.asitplus.wallet.backend.config

import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.*
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse

const val MDC_KEY = "txID"

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestResponseLoggingFilter : Filter {
    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain
    ) {
        MDC.put(MDC_KEY, UUID.randomUUID().toString())
        chain.doFilter(request, response)

    }
}