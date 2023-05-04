package at.asitplus.wallet.backend.auth

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser


interface AuthenticationSupplier {

    fun getCurrentUserCertificate(): ByteArray?

    fun getCurrentUserOidcDetails(): OidcIdToken?

}

class SpringSecurityAuthenticationSupplier : AuthenticationSupplier {

    override fun getCurrentUserCertificate(): ByteArray? {
        val principal = SecurityContextHolder.getContext()?.authentication?.principal
        if (principal !is AuthenticatedDeviceBindingUser)
            return null
        return principal.certificate
    }

    override fun getCurrentUserOidcDetails(): OidcIdToken? {
        val principal = SecurityContextHolder.getContext()?.authentication?.principal
        if (principal !is OidcUser)
            return null
        return principal.idToken
    }

}