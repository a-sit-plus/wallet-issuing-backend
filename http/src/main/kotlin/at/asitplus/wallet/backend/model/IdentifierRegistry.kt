package at.asitplus.wallet.backend.model

import org.springframework.stereotype.Service
import java.io.*


@Service
class IdentifierRegistry(private val identifierRepository: IdentifierRepository) {

    @Throws(Exception::class)
    fun addIdentifier(key: String) {
        val identity: Identifier? = identifierRepository.findByKey(key)
        identity?.run { throw Exception("Already registered") } ?: identifierRepository.save(Identifier(key, false))
    }

    @Throws(Exception::class)
    fun isRevoked(key: String): Boolean {
        val identity: Identifier? = identifierRepository.findByKey(key)
        return identity?.revoked ?: throw Exception("Not registered")
    }

    @Throws(Exception::class)
    fun revoke(key: String) {
        val identity: Identifier? = identifierRepository.findByKey(key)
        identity?.let {
            it.revoked = true
            identifierRepository.save(it)
        } ?: throw Exception("Not registered")
    }

    fun getRevocationList(): BooleanArray {
        return identifierRepository.getAllRevoked().toBooleanArray()
    }

}