package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.NonceAuthenticationProvider
import at.asitplus.wallet.backend.auth.NonceAuthnFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.session.MapSessionRepository
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession
import org.springframework.session.web.http.HeaderHttpSessionIdResolver
import org.springframework.session.web.http.HttpSessionIdResolver
import java.util.concurrent.ConcurrentHashMap


@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableSpringHttpSession
class WebSecurityConfig(val nonceAuthenticationProvider: NonceAuthenticationProvider) : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        http.csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED).and()
            .addFilter(NonceAuthnFilter().apply { setAuthenticationManager(authenticationManager()) })
    }

    override fun configure(auth: AuthenticationManagerBuilder) {
        auth.authenticationProvider(nonceAuthenticationProvider)
    }

    @Bean
    fun sessionRepository(): MapSessionRepository {
        return MapSessionRepository(ConcurrentHashMap())
    }

    @Bean
    fun httpSessionIdResolver(): HttpSessionIdResolver {
        return HeaderHttpSessionIdResolver.xAuthToken()
    }

}