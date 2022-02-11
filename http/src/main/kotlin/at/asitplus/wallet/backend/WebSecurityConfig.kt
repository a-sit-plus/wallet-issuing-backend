package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.DeviceBindingAuthenticationEntryPoint
import at.asitplus.wallet.backend.auth.DeviceBindingAuthenticationProvider
import at.asitplus.wallet.backend.auth.DeviceBindingAuthnFilter
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
class WebSecurityConfig(
    private val deviceBindingAuthenticationProvider: DeviceBindingAuthenticationProvider,
    private val nonceAuthenticationProvider: NonceAuthenticationProvider,
    private val deviceBindingAuthnChallengeService: ChallengeService,
) : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        http.csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED).and()
            .addFilter(DeviceBindingAuthnFilter().apply { setAuthenticationManager(authenticationManager()) })
            .addFilter(NonceAuthnFilter().apply { setAuthenticationManager(authenticationManager()) })
            .exceptionHandling()
            .authenticationEntryPoint(DeviceBindingAuthenticationEntryPoint(deviceBindingAuthnChallengeService)).and()
            .logout().invalidateHttpSession(true).clearAuthentication(true).and()
    }

    override fun configure(auth: AuthenticationManagerBuilder) {
        auth.authenticationProvider(deviceBindingAuthenticationProvider)
            .authenticationProvider(nonceAuthenticationProvider)
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
