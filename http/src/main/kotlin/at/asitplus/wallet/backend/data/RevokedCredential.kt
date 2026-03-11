package at.asitplus.wallet.backend.data

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
class RevokedCredential() {

    constructor(
        timePeriod: Int,
        revocationListIndex: Long,
        identifier: Long,
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
    var identifier: Long = 0L

    @Column
    var userInfoSubject: String? = null

    override fun toString(): String {
        return "RevokedCredential(" +
                "timePeriod=$timePeriod, " +
                "identifier=$identifier, " +
                "revocationListIndex=$revocationListIndex, " +
                "status=$status, " +
                "userInfoSubject=$userInfoSubject" +
                ")"
    }

}