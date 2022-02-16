package at.asitplus.wallet.backend.auth

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * Is created by [DeviceBindingAuthenticationProvider], which validates response to challenge from [DeviceBindingAuthenticationToken].
 */
class AuthenticatedDeviceBindingToken(bpk: String, certificate: ByteArray) :
    AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("DEVICE_BINDING"))) {

    private val principal = AuthenticatedDeviceBindingUser(bpk, certificate)

    override fun isAuthenticated(): Boolean {
        return true
    }

    override fun getCredentials(): Any? {
        return null
    }

    override fun getPrincipal(): Any {
        return principal
    }

}