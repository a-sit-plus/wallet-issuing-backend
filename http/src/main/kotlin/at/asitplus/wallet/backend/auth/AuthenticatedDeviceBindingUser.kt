package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.lib.encodeBase64
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * Represents a successful device binding authentication from the Wallet App.
 * Is created by [DeviceBindingAuthnProvider], which validates response to challenge from [DeviceBindingAuthnToken].
 */
class AuthenticatedDeviceBindingUser(val bpk: String, val certificate: ByteArray) :
    UserDetails {
    override fun getAuthorities(): List<SimpleGrantedAuthority> {
        return listOf(SimpleGrantedAuthority("DEVICE_BINDING"))
    }

    override fun getPassword(): String? {
        return null
    }

    override fun getUsername(): String {
        return bpk
    }

    override fun isAccountNonExpired(): Boolean {
        return true
    }

    override fun isAccountNonLocked(): Boolean {
        return true
    }

    override fun isCredentialsNonExpired(): Boolean {
        return true
    }

    override fun isEnabled(): Boolean {
        return true
    }

    override fun toString(): String {
        return "AuthenticatedDeviceBindingUser(bpk='$bpk', certificate=${certificate.encodeBase64()})"
    }

}