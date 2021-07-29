package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.toBase64Url
import org.springframework.stereotype.Service
import java.security.SecureRandom

@Service
class AuthTokenService {

    private val activeTokens = mutableListOf<String>()

    fun generateAuthToken(): String {
        val bytes = ByteArray(128)
        SecureRandom().nextBytes(bytes)
        val token = bytes.toBase64Url()
        activeTokens += token
        return token
    }

    fun validateToken(token: String): Boolean {
        return activeTokens.remove(token)
    }

}