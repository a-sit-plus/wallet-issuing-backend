package at.asitplus.wallet.backend.data

import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.OneToMany

@Entity
class IssuedCredential() {

    constructor(
        vcId: String,
        subjectId: String,
        validUntil: Instant,
        deviceBinding: DeviceBinding,
        attributeName: String
    ) : this() {
        this.vcId = vcId
        this.subjectId = subjectId
        this.validUntil = validUntil
        this.deviceBinding = deviceBinding
        this.attributeName = attributeName
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
    @GeneratedValue
    val revocationListIndex: Long = 0L

}
