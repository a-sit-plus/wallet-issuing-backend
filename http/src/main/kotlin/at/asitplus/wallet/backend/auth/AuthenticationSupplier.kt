package at.asitplus.wallet.backend.auth

import org.springframework.security.core.context.SecurityContextHolder


interface AuthenticationSupplier {
    fun getCurrentUserCertificate(): ByteArray?
}

class SpringSecurityAuthenticationSupplier : AuthenticationSupplier {

    override fun getCurrentUserCertificate(): ByteArray? {
        val principal = SecurityContextHolder.getContext()?.authentication?.principal
        if (principal !is AuthenticatedDeviceBindingUser)
            return null
        return principal.certificate
    }

}