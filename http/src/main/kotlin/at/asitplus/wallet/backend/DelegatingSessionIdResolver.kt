package at.asitplus.wallet.backend

import org.springframework.session.web.http.HttpSessionIdResolver
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * Used in EIDAS deployments to set session identifier to
 * header `X-Auth-Token` and cookie `SESSION`.
 */
class DelegatingSessionIdResolver(private vararg val resolvers: HttpSessionIdResolver) : HttpSessionIdResolver {

    override fun resolveSessionIds(request: HttpServletRequest?): MutableList<String> {
        return resolvers.map { it.resolveSessionIds(request) }.flatten().toMutableList()
    }


    override fun setSessionId(request: HttpServletRequest?, response: HttpServletResponse?, sessionId: String?) {
        resolvers.forEach {
            it.setSessionId(request, response, sessionId)
        }
    }

    override fun expireSession(request: HttpServletRequest?, response: HttpServletResponse?) {
        resolvers.forEach {
            it.expireSession(request, response)
        }
    }

}