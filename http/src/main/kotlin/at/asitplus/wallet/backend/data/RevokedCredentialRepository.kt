package at.asitplus.wallet.backend.data

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
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

}

internal object CredentialRepositoriesLock

@Entity
class RevokedCredential() {

    constructor(
        revocationListIndex: Long,
        timePeriod: Int,
        status: UByte,
    ) : this() {
        this.timePeriod = timePeriod
        this.revocationListIndex = revocationListIndex
        this.status = status
    }

    @Id
    var revocationListIndex: Long = 0L

    @Column
    var timePeriod: Int = 0

    @Column
    var status: UByte = 0u

    override fun toString(): String {
        return "RevokedCredential(" +
                "revocationListIndex=$revocationListIndex, " +
                "timePeriod=$timePeriod, " +
                "status=$status" +
                ")"
    }

}