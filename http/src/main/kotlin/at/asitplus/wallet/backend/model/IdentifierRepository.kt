package at.asitplus.wallet.backend.model

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface IdentifierRepository : JpaRepository<Identifier, Long> {
    fun findByKey(key: String): Identifier?
    @Query("select i.revoked from Identifier i")
    fun getAllRevoked(): List<Boolean>
}