package at.asitplus.wallet.backend.config

import at.asitplus.wallet.backend.Paths
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler
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
class WebSecurityConfig(
    @Value("\${spring.boot.admin.client.enabled:false}") private val adminClientEnabled: Boolean,
    @Value("\${spring.boot.admin.client.instance.metadata.user.name:#{null}}") private val adminUsername: String?,
    @Value("\${spring.boot.admin.client.instance.metadata.user.password:#{null}}") private val adminPassword: String?,
) {

    // Non-null only when the admin client is enabled and both credentials are provided.
    private val actuatorCredentials: Pair<String, String>?
        get() = if (adminClientEnabled && adminUsername != null && adminPassword != null)
        adminUsername to adminPassword
        else null

    @Bean
    @Order(1)
    fun actuatorSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher("/actuator/**")
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .csrf { it.disable() }
        val creds = actuatorCredentials
        if (creds != null) {
            http.authorizeHttpRequests { it.anyRequest().hasRole("ACTUATOR") }
                .httpBasic(Customizer.withDefaults())
        } else {
            http.authorizeHttpRequests { it.anyRequest().denyAll() }
        }
        return http.build()
    }

    @Bean
    @Order(2)
    fun securityFilterChain(
        http: HttpSecurity,
        clientRegistrations: InMemoryClientRegistrationRepository?
    ): SecurityFilterChain {
        val builder = http.csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.ALWAYS) }
            .logout {
                it.invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .logoutSuccessUrl("/")
            }.headers {
                it.frameOptions { it.sameOrigin() }
            }.authorizeHttpRequests {
                it.requestMatchers(Paths.AuthorizeUrl).authenticated()
                it.anyRequest().permitAll()
            }.formLogin {
                it.loginPage(Paths.LoginUrl)
            }
        if (clientRegistrations != null) {
            builder.oauth2Login {
                it.defaultSuccessUrl("/").loginPage(Paths.LoginUrl)
            }
        }
        return builder
            .build()
    }

    @Bean
    fun userDetailsService(
        @Value("\${spring.security.user.name:user}") demoUsername: String,
        @Value("\${spring.security.user.password:#{null}}") demoPassword: String?,
    ): UserDetailsService {
        val demoUser = demoPassword?.let {
            User.withUsername(demoUsername).password("{noop}$it").roles("USER").build()
        }
        val (actuatorUsername, actuatorPassword) = actuatorCredentials
            ?: return InMemoryUserDetailsManager(listOfNotNull(demoUser))
        return InMemoryUserDetailsManager(listOfNotNull(
            demoUser,
            User.withUsername(actuatorUsername).password("{noop}$actuatorPassword").roles("ACTUATOR").build(),
        ))
    }

    @Bean
    fun sessionRepository() = MapSessionRepository(ConcurrentHashMap())

    /**
     * We need cookie-based sessions on the Web, and header-based sessions for mobile clients
     */
    @Bean
    fun httpSessionIdResolver() =
        DelegatingSessionIdResolver(CookieHttpSessionIdResolver(), HeaderHttpSessionIdResolver.xAuthToken())

    @Bean
    fun successHandler() = SavedRequestAwareAuthenticationSuccessHandler()
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
