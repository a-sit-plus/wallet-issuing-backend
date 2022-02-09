package at.asitplus.wallet.backend.auth

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * Is created by [DeviceBindingAuthenticationProvider], which validates response to challenge from [DeviceBindingAuthenticationToken].
 */
class AuthenticatedDeviceBindingToken(private val bpk: String) :
    AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("DEVICE_BINDING"))) {

    override fun isAuthenticated(): Boolean {
        return true
    }

    override fun getCredentials(): Any? {
        return null
    }

    override fun getPrincipal(): Any {
        return bpk
    }

}