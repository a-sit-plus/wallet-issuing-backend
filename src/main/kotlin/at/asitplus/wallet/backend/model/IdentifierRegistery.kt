package at.asitplus.wallet.backend.model

import org.springframework.stereotype.Service

@Service
class IdentifierRegistery {

    data class Identifier(val key: String, val revoked: Boolean)

    private var register: MutableMap<String, Boolean> = mutableMapOf()

    fun addIdenitfier (key: String) {
        if (!register.containsKey(key))
            register[key] = false
        else
            throw Exception("Already registered") //TODO make own Exception?
    }

    fun isRevoked (key: String) : Boolean {
        return register[key] == true
    }

    fun revoke (key: String) {
        register[key] = true
    }


}