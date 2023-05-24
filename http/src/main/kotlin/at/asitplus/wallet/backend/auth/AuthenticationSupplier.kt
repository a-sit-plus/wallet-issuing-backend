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
        val authn = SecurityContextHolder.getContext()?.authentication
            ?: return null
        if (authn is OidcUser)
            return authn.idToken
        if (authn is DeviceBindingAuthnToken)
            return authn.getOidcIdToken()
        val principal = authn.principal
        if (principal is OidcUser)
            return principal.idToken
        return null
    }

}