package at.asitplus.wallet.backend.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Be sure to use [CredentialRepositoriesLock] when executing modifying statements
 */
@Repository
interface PreparedCredentialRepository : JpaRepository<PreparedCredential, Long> {

    @Query("select max(i.revocationListIndex) from PreparedCredential i where i.timePeriod = :timePeriod")
    fun getMaxRevocationListIndex(@Param("timePeriod") timePeriod: Int): Long?

}