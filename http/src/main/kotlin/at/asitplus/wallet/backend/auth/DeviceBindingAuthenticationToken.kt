package at.asitplus.wallet.backend.auth

import org.springframework.security.authentication.AbstractAuthenticationToken

/**
 * Is created from the HTTP header value in [DeviceBindingAuthnFilter].
 * Gets exchanged into an [AuthenticatedDeviceBindingToken] in [DeviceBindingAuthenticationProvider].
 */
class DeviceBindingAuthenticationToken(val response: String) :
    AbstractAuthenticationToken(null) {

    override fun getCredentials(): Any {
        return response
    }

    override fun getPrincipal(): Any? {
        return null
    }

}