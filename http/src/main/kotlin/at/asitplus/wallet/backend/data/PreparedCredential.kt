package at.asitplus.wallet.backend.data

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
class PreparedCredential() {

    constructor(
        timePeriod: Int,
        revocationListIndex: Long,
    ) : this() {
        this.timePeriod = timePeriod
        this.revocationListIndex = revocationListIndex
    }

    @Id
    @GeneratedValue
    var id: Long = 0

    @Column
    @CreationTimestamp
    lateinit var createdOn: Instant

    @Column
    var timePeriod: Int = 0

    @Column
    var revocationListIndex: Long = 0L

    override fun toString(): String {
        return "PreparedCredential(id=$id, " +
                "createdOn=$createdOn, " +
                "timePeriod=$timePeriod, " +
                "revocationListIndex=$revocationListIndex)"
    }
}
