package at.asitplus.wallet.backend.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Be sure to use [CredentialRepositoriesLock] when executing modifying statements
 */
@Repository
interface RevokedCredentialRepository : JpaRepository<RevokedCredential, Long> {
    @Query("select i.revocationListIndex from RevokedCredential i where i.timePeriod = :timePeriod order by i.revocationListIndex")
    fun getRevocationListIndexByTimePeriod(@Param("timePeriod") timePeriod: Int): Collection<Long>

    @Query("select i from RevokedCredential i where i.timePeriod = :timePeriod order by i.revocationListIndex")
    fun getByTimePeriod(@Param("timePeriod") timePeriod: Int): Collection<RevokedCredential>

    @Query("select max(i.revocationListIndex) from RevokedCredential i where i.timePeriod = :timePeriod")
    fun getMaxRevocationListIndex(@Param("timePeriod") timePeriod: Int): Long?

    fun findAllByUserInfoSubject(userInfoSubject: String?): Collection<RevokedCredential>
}

internal object CredentialRepositoriesLock

