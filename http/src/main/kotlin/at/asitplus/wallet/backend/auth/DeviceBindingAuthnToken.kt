package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.auth.WebSecurityConstants.AUTHORITY_DEVICE_BINDING
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * Authentication response from the Wallet App, containing
 * the `response` (should be a JWS object), and possibly the `certificate` and `bpk`,
 * if the user has been successfully authenticated.
 */
class DeviceBindingAuthnToken : AbstractAuthenticationToken {

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
     * Called from [DeviceBindingAuthnProvider]
     * after successful authentication
     */
    constructor(
        response: String,
        bpk: String,
        certificate: ByteArray
    ) : super(listOf(SimpleGrantedAuthority(AUTHORITY_DEVICE_BINDING))) {
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

    override fun toString(): String {
        return "DeviceBindingAuthnToken(principal='$principal', response='$response')"
    }

}