package at.asitplus.wallet.backend.data

import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

@Repository
interface RevokedCredentialRepository : JpaRepository<RevokedCredential, Long> {

    fun findByTimePeriodOrderByRevocationListIndexAsc(
        timePeriod: Int
    ): MutableCollection<RevokedCredential>


    @Query("select i.revocationListIndex from RevokedCredential i where i.timePeriod = ?1 order by i.revocationListIndex")
    fun getRevocationListIndexByTimePeriod(timePeriod: Int): Collection<Long>

    @Query("select max(i.revocationListIndex) from RevokedCredential i")
    fun getMaxRevocationListIndex(): Long?

}


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