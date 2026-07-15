package at.asitplus.wallet.backend.data

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
class IssuedCredential() {

    constructor(
        vcId: String,
        subjectId: String,
        userInfoSubject: String,
        validUntil: Instant,
        timePeriod: Int,
        attributeName: String,
        revocationListIndex: Long,
    ) : this() {
        this.vcId = vcId
        this.subjectId = subjectId
        this.userInfoSubject = userInfoSubject
        this.attributeName = attributeName
        this.validUntil = validUntil
        this.timePeriod = timePeriod
        this.revocationListIndex = revocationListIndex
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column
    @CreationTimestamp
    lateinit var createdOn: Instant

    @Column
    lateinit var vcId: String

    @Column
    lateinit var subjectId: String

    @Column
    lateinit var attributeName: String

    @Column
    lateinit var validUntil: Instant

    @Column
    var userInfoSubject: String? = null

    @Column
    var timePeriod: Int = 0

    @Column
    var revoked: Boolean = false

    @Column
    var revocationListIndex: Long = 0L

    override fun toString(): String {
        return "IssuedCredential(id=$id, " +
                "createdOn=$createdOn, " +
                "vcId='$vcId', " +
                "subjectId='$subjectId', " +
                "attributeName='$attributeName', " +
                "validUntil=$validUntil, " +
                "userInfoSubject=$userInfoSubject, " +
                "timePeriod=$timePeriod, " +
                "revocationListIndex=$revocationListIndex, " +
                "revoked=$revoked)"
    }
}
