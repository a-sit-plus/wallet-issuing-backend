package at.asitplus.wallet.backend

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy


@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
class WebSecurityConfig(val nonceAuthenticationProvider: NonceAuthenticationProvider) : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        http.csrf().disable()
            // TODO sessionFixation
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED).and()
            .addFilter(NonceAuthnFilter().apply { setAuthenticationManager(authenticationManager()) })
    }

    override fun configure(auth: AuthenticationManagerBuilder?) {
        auth?.authenticationProvider(nonceAuthenticationProvider)
    }

}