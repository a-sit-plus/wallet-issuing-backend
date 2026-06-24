package at.asitplus.wallet.backend.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Be sure to use [CredentialRepositoriesLock] when executing modifying statements
 */
@Repository
interface IssuedCredentialRepository : JpaRepository<IssuedCredential, Long> {

    fun findBytimePeriodAndVcId(timePeriod: Int, vcId: String): IssuedCredential?

    fun findAllByValidUntilAfter(validUntil: Instant): Collection<IssuedCredential>

    @Query("select max(i.revocationListIndex) from IssuedCredential i where i.timePeriod = :timePeriod")
    fun getMaxRevocationListIndex(@Param("timePeriod") timePeriod: Int): Long?

    fun findAllByValidUntilBefore(cutoff: Instant): Collection<IssuedCredential>


}