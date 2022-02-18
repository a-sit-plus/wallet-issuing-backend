package at.asitplus.wallet.backend.auth

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
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

}