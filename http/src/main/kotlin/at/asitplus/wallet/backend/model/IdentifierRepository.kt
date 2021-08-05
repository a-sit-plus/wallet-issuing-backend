package at.asitplus.wallet.backend.model

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface IdentifierRepository : JpaRepository<Identifier, Long> {

    fun findByKey(key: String): Identifier?

    fun findAllByRevokedFalse(): Collection<Identifier>

    fun findAllByRevokedTrueOrderByRevocationListIndex(): Collection<Identifier>

}