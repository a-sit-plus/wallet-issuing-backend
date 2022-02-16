package at.asitplus.wallet.backend.auth

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class DeviceBindingAuthenticationToken : AbstractAuthenticationToken {

    val response: String
    private val principal: UserDetails?

    /**
     * Called from [DeviceBindingAuthnFilter]
     */
    constructor(response: String) : super(null) {
        this.response = response
        this.principal = null
        this.isAuthenticated = false
    }

    /**
     * Called from [DeviceBindingAuthenticationProvider]
     * after successful authentication
     */
    constructor(
        response: String,
        bpk: String,
        certificate: ByteArray
    ) : super(listOf(SimpleGrantedAuthority("DEVICE_BINDING"))) {
        this.response = response
        this.principal = AuthenticatedDeviceBindingUser(bpk, certificate)
        this.isAuthenticated = true
    }

    override fun getCredentials(): Any {
        return response
    }

    override fun getPrincipal(): Any? {
        return principal
    }

}