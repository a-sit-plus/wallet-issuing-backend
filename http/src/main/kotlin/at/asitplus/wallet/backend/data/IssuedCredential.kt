package at.asitplus.wallet.backend.data

import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

@Entity
class IssuedCredential() {

    constructor(
        vcId: String,
        subjectId: String,
        validUntil: Instant,
        deviceBinding: DeviceBinding,
        attributeName: String,
        revocationListIndex: Long,
    ) : this() {
        this.vcId = vcId
        this.subjectId = subjectId
        this.attributeName = attributeName
        this.validUntil = validUntil
        this.revoked = false
        this.deviceBinding = deviceBinding
        this.revocationListIndex = revocationListIndex
    }

    @Id
    @GeneratedValue
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
    var revoked: Boolean = false

    @ManyToOne
    @JoinColumn(name = "device_binding_id", referencedColumnName = "id")
    lateinit var deviceBinding: DeviceBinding

    @Column
    var revocationListIndex: Long = 0L

    override fun toString(): String {
        return "IssuedCredential(id=$id, " +
                "createdOn=$createdOn, " +
                "vcId='$vcId', " +
                "subjectId='$subjectId', " +
                "attributeName='$attributeName', " +
                "validUntil=$validUntil, " +
                "revoked=$revoked, " +
                "deviceBinding=${deviceBinding.id}, " +
                "revocationListIndex=$revocationListIndex)"
    }


}
