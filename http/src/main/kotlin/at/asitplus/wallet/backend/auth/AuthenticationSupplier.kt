package at.asitplus.wallet.backend.auth

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser


interface AuthenticationSupplier {

    fun getCurrentUserOidcDetails(): OidcIdToken?

}

class SpringSecurityAuthenticationSupplier : AuthenticationSupplier {

    override fun getCurrentUserOidcDetails(): OidcIdToken? {
        val authn = SecurityContextHolder.getContext()?.authentication
            ?: return null
        if (authn is OidcUser)
            return authn.idToken
        val principal = authn.principal
        if (principal is OidcUser)
            return principal.idToken
        return null
    }

}