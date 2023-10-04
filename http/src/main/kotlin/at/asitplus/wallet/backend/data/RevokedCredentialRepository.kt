package at.asitplus.wallet.backend.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id

/**
 * Be sure to use [CredentialRepositoriesLock] when executing modifying statements
 */
@Repository
interface RevokedCredentialRepository : JpaRepository<RevokedCredential, Long> {
    @Query("select i.revocationListIndex from RevokedCredential i where i.timePeriod = :timePeriod order by i.revocationListIndex")
    fun getRevocationListIndexByTimePeriod(@Param("timePeriod") timePeriod: Int): Collection<Long>

    @Query("select max(i.revocationListIndex) from RevokedCredential i where i.timePeriod = :timePeriod")
    fun getMaxRevocationListIndex(@Param("timePeriod") timePeriod: Int): Long?

}

internal object CredentialRepositoriesLock

@Entity
class RevokedCredential() {

    constructor(
        timePeriod: Int,
        revocationListIndex: Long,
    ) : this() {
        this.timePeriod = timePeriod
        this.revocationListIndex = revocationListIndex
    }

    @Id
    var revocationListIndex: Long = 0L

    @Column
    var timePeriod: Int = 0

    override fun toString(): String =
        "RevokedCredential(revocationListIndex=$revocationListIndex," +
                "timePeriod=$timePeriod" +
                ")"
}