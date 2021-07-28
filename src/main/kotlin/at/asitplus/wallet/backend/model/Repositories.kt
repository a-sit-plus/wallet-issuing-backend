package at.asitplus.wallet.backend.model

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import javax.transaction.Transactional

@Repository
interface IdentifierRepository : JpaRepository<Identifier, Long> {
    fun findByKey(key: String): Identifier?
}