package at.asitplus.wallet.backend.data

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
class RevokedCredential() {

    constructor(
        revocationListIndex: Long,
        timePeriod: Int,
        status: UByte,
        userInfoSubject: String?,
    ) : this() {
        this.timePeriod = timePeriod
        this.revocationListIndex = revocationListIndex
        this.status = status
        this.userInfoSubject = userInfoSubject
    }

    @Id
    var revocationListIndex: Long = 0L

    @Column
    var timePeriod: Int = 0

    @Column
    var status: UByte = 0u

    @Column
    var userInfoSubject: String? = null

    override fun toString(): String {
        return "RevokedCredential(" +
                "revocationListIndex=$revocationListIndex, " +
                "timePeriod=$timePeriod, " +
                "status=$status, " +
                "userInfoSubject=$userInfoSubject" +
                ")"
    }

}