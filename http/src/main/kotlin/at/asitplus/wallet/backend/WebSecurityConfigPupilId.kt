package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.ApiKeyAuthnFilter
import at.asitplus.wallet.backend.auth.ApiKeyAuthnProvider
import at.asitplus.wallet.backend.auth.DeviceBindingAuthnEntryPoint
import at.asitplus.wallet.backend.auth.DeviceBindingAuthnFilter
import at.asitplus.wallet.backend.auth.DeviceBindingAuthnProvider
import at.asitplus.wallet.backend.auth.ExtNonceAuthnFilter
import at.asitplus.wallet.backend.auth.ExtNonceAuthnProvider
import at.asitplus.wallet.backend.auth.ExtNonceLogoutHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.session.MapSessionRepository
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession
import org.springframework.session.web.http.HeaderHttpSessionIdResolver
import org.springframework.session.web.http.HttpSessionIdResolver
import java.util.concurrent.ConcurrentHashMap

@Profile("pupilid")
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableSpringHttpSession
class WebSecurityConfigPupilId(
    private val deviceBindingAuthnProvider: DeviceBindingAuthnProvider,
    private val extNonceAuthnProvider: ExtNonceAuthnProvider,
    private val deviceBindingAuthnChallengeService: ChallengeService,
    private val apiKeyAuthnProvider: ApiKeyAuthnProvider,
    private val extNonceLogoutHandler: ExtNonceLogoutHandler,
) : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        http.csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED).and()
            .addFilter(DeviceBindingAuthnFilter().apply { setAuthenticationManager(authenticationManager()) })
            .addFilter(ExtNonceAuthnFilter().apply { setAuthenticationManager(authenticationManager()) })
            .addFilter(ApiKeyAuthnFilter().apply { setAuthenticationManager(authenticationManager()) })
            .exceptionHandling()
            .defaultAuthenticationEntryPointFor(
                DeviceBindingAuthnEntryPoint(deviceBindingAuthnChallengeService),
                AntPathRequestMatcher("/pupilid/**")
            )
            .defaultAuthenticationEntryPointFor(Http403ForbiddenEntryPoint(), AntPathRequestMatcher("/**"))
            .and().logout().invalidateHttpSession(true).clearAuthentication(true)
            .addLogoutHandler(extNonceLogoutHandler)
            .and().headers().frameOptions().sameOrigin()
    }

    override fun configure(auth: AuthenticationManagerBuilder) {
        auth.authenticationProvider(deviceBindingAuthnProvider)
            .authenticationProvider(extNonceAuthnProvider)
            .authenticationProvider(apiKeyAuthnProvider)
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
