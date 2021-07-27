package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.data.toBase64Url
import org.springframework.stereotype.Service
import java.security.SecureRandom

@Service
class AuthTokenService {

    private val activeTokens = mutableListOf<String>()

    fun generateAuthToken(): String {
        val bytes = byteArrayOf(32)
        SecureRandom().nextBytes(bytes)
        val token = bytes.toBase64Url()
        activeTokens += token
        return token
    }

    fun validateToken(token: String): Boolean {
        return activeTokens.remove(token)
    }

}