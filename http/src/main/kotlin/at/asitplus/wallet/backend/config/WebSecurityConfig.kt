package at.asitplus.wallet.backend.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.session.MapSessionRepository
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession
import org.springframework.session.web.http.CookieHttpSessionIdResolver
import org.springframework.session.web.http.HeaderHttpSessionIdResolver
import org.springframework.session.web.http.HttpSessionIdResolver
import java.util.concurrent.ConcurrentHashMap


/**
 * Web security configuration
 */
@Configuration
@EnableMethodSecurity
@EnableSpringHttpSession
class WebSecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain = http.csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.ALWAYS) }
        .logout {
            it.invalidateHttpSession(true)
                .clearAuthentication(true)
                .logoutSuccessUrl("/")
        }.headers {
            it.frameOptions { it.sameOrigin() }
        }.authorizeHttpRequests{
            it.requestMatchers("/authorize").authenticated()
            it.anyRequest().permitAll()
        }.oauth2Login {
            it.defaultSuccessUrl("/").loginPage("/login")
        }.formLogin {
            it.loginPage("/login")
        }
        .build()

    @Bean
    fun userDetailsService() = InMemoryUserDetailsManager(
        User.withDefaultPasswordEncoder()
            .username("user")
            .password("password")
            .roles("USER")
            .build()
    )

    @Bean
    fun sessionRepository() = MapSessionRepository(ConcurrentHashMap())

    /**
     * We need cookie-based sessions on the Web, and header-based sessions for mobile clients
     */
    @Bean
    fun httpSessionIdResolver() =
        DelegatingSessionIdResolver(CookieHttpSessionIdResolver(), HeaderHttpSessionIdResolver.xAuthToken())

}

/**
 * Set session identifier into header `X-Auth-Token` (App clients) and cookie `SESSION` (Web clients).
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
