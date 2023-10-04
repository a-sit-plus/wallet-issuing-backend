package at.asitplus.wallet.backend.config

import at.asitplus.wallet.backend.DelegatingSessionIdResolver
import at.asitplus.wallet.backend.ProfileConstants
import at.asitplus.wallet.backend.auth.ApiKeyAuthnFilter
import at.asitplus.wallet.backend.auth.ApiKeyAuthnProvider
import at.asitplus.wallet.backend.auth.DeviceBindingAuthnEntryPoint
import at.asitplus.wallet.backend.auth.DeviceBindingAuthnFilter
import at.asitplus.wallet.backend.auth.DeviceBindingAuthnProvider
import at.asitplus.wallet.backend.auth.ExtNonceAuthnFilter
import at.asitplus.wallet.backend.auth.ExtNonceAuthnProvider
import at.asitplus.wallet.backend.auth.ExtNonceLogoutHandler
import at.asitplus.wallet.backend.auth.WebSecurityConstants
import at.asitplus.wallet.backend.service.ChallengeService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.lang.Nullable
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.session.MapSessionRepository
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession
import org.springframework.session.web.http.CookieHttpSessionIdResolver
import org.springframework.session.web.http.HeaderHttpSessionIdResolver
import org.springframework.session.web.http.HttpSessionIdResolver
import org.springframework.util.StringUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * Web security configuration for the PupilId deployment:
 * - Ext. Nonce authentication from the App
 * - Device Binding authentication from the App
 * - API-Key authentication for revocation calls from ext. services
 */
@Profile(ProfileConstants.PUPILID)
@Configuration
@EnableMethodSecurity
@EnableSpringHttpSession
class WebSecurityConfigPupilId(
    private val deviceBindingAuthnProvider: DeviceBindingAuthnProvider,
    private val extNonceAuthnProvider: ExtNonceAuthnProvider,
    private val deviceBindingAuthnChallengeService: ChallengeService,
    private val apiKeyAuthnProvider: ApiKeyAuthnProvider,
    private val extNonceLogoutHandler: ExtNonceLogoutHandler,
) {

    @Bean
    fun filterChain(
        http: HttpSecurity,
        authenticationManager: AuthenticationManager,
        environment: Environment,
        @Nullable clientRegistrationRepository: ClientRegistrationRepository?,
    ): SecurityFilterChain? {
        http.csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .addFilter(DeviceBindingAuthnFilter().apply { setAuthenticationManager(authenticationManager) })
            .addFilter(ExtNonceAuthnFilter().apply { setAuthenticationManager(authenticationManager) })
            .addFilter(ApiKeyAuthnFilter().apply { setAuthenticationManager(authenticationManager) })
            .exceptionHandling {
                it.defaultAuthenticationEntryPointFor(
                    DeviceBindingAuthnEntryPoint(deviceBindingAuthnChallengeService),
                    AntPathRequestMatcher("/pupilid/**")
                )
            }.logout {
                it.invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .addLogoutHandler(extNonceLogoutHandler)
            }.headers { it.frameOptions { it.sameOrigin() } }
        if (environment.acceptsProfiles(Profiles.of(ProfileConstants.AUTHN_IDA)) && clientRegistrationRepository != null) {
            val loginUrl = getOidcLoginUrl(clientRegistrationRepository)
            http.exceptionHandling {
                it.defaultAuthenticationEntryPointFor(
                    LoginUrlAuthenticationEntryPoint(loginUrl),
                    AntPathRequestMatcher("/pupilid/consent/retrieve")
                )
                it.defaultAuthenticationEntryPointFor(
                    LoginUrlAuthenticationEntryPoint(loginUrl),
                    AntPathRequestMatcher("/binding/**")
                )
                it.defaultAuthenticationEntryPointFor(
                    Http403ForbiddenEntryPoint(),
                    AntPathRequestMatcher("/**")
                )
            }.oauth2Login {
                it.defaultSuccessUrl("/pupilid/consent/retrieve")
                    .userInfoEndpoint { it.oidcUserService(oidcUserService()) }
            }
        } else {
            http.exceptionHandling {
                it.defaultAuthenticationEntryPointFor(
                    Http403ForbiddenEntryPoint(),
                    AntPathRequestMatcher("/**")
                )
            }
        }
        return http.build()
    }

    private fun getOidcLoginUrl(clientRegistrationRepository: ClientRegistrationRepository): String {
        if (clientRegistrationRepository is InMemoryClientRegistrationRepository) {
            val iterator = clientRegistrationRepository.iterator()
            // TODO Once Spring provides a way to get that URL more robust, use this
            if (iterator.hasNext())
                return "/oauth2/authorization/${iterator.next().registrationId}"
        }
        return "/login"
    }

    /**
     * Adapted from Spring's [OidcUserService] to set the authority `OIDC_IDA` [WebSecurityConstants.AUTHORITY_OIDC]
     */
    fun oidcUserService(): OidcUserService = object : OidcUserService() {
        override fun loadUser(userRequest: OidcUserRequest?): OidcUser {
            require(userRequest != null) { "userRequest cannot be null" }
            val userInfo: OidcUserInfo? = null
            val authorities: MutableSet<GrantedAuthority> = LinkedHashSet()
            authorities.add(OidcUserAuthority(WebSecurityConstants.AUTHORITY_OIDC, userRequest.idToken, userInfo))
            userRequest.accessToken.scopes.mapTo(authorities) { SimpleGrantedAuthority("SCOPE_$it") }
            return getUser(userRequest, userInfo, authorities)
        }

        private fun getUser(
            userRequest: OidcUserRequest,
            userInfo: OidcUserInfo?,
            authorities: Set<GrantedAuthority>
        ): OidcUser {
            val providerDetails = userRequest.clientRegistration.providerDetails
            val userNameAttributeName = providerDetails.userInfoEndpoint.userNameAttributeName
            return if (StringUtils.hasText(userNameAttributeName)) {
                DefaultOidcUser(authorities, userRequest.idToken, userInfo, userNameAttributeName)
            } else DefaultOidcUser(authorities, userRequest.idToken, userInfo)
        }
    }

    @Bean
    fun authenticationManager(): AuthenticationManager {
        return ProviderManager(deviceBindingAuthnProvider, extNonceAuthnProvider, apiKeyAuthnProvider)
    }

    @Bean
    fun sessionRepository(): MapSessionRepository {
        return MapSessionRepository(ConcurrentHashMap())
    }

    @Bean
    fun httpSessionIdResolver(environment: Environment): HttpSessionIdResolver {
        if (environment.acceptsProfiles(Profiles.of(ProfileConstants.AUTHN_IDA))) {
            /**
             * We need cookie-based sessions on the Web, and header-based sessions for mobile clients
             */
            return DelegatingSessionIdResolver(CookieHttpSessionIdResolver(), HeaderHttpSessionIdResolver.xAuthToken())
        } else {
            return HeaderHttpSessionIdResolver.xAuthToken()
        }
    }

}
