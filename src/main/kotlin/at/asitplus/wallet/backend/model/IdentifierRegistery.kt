package at.asitplus.wallet.backend.model

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import javax.transaction.Transactional

@Service
class IdentifierRegistery(private val identifierRepository: IdentifierRepository) {

    fun addIdenitfier (key: String) {
        val identity: Identifier? = identifierRepository.findByKey(key)
        identity?.run { throw Exception("Already registered") } ?: identifierRepository.save(Identifier(key, false))
    }

    fun isRevoked (key: String) : Boolean {
        val identity: Identifier? = identifierRepository.findByKey(key)
        return identity?.revoked ?: throw Exception("Not registered")
    }

    fun revoke (key: String) {
        val identity: Identifier? = identifierRepository.findByKey(key)
        identity?.let {
            it.revoked = true
            identifierRepository.save(it)
        } ?: throw Exception("Not registered")
    }

}