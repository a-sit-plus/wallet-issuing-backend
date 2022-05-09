package at.asitplus.wallet.backend.data

import at.asitplus.wallet.lib.encodeBase64
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Lob
import javax.persistence.OneToMany

@Entity
class DeviceBinding() {

    constructor(
        bpk: String,
        certificate: ByteArray,
        deviceName: String,
        deviceId: String,
        validUntil: Instant
    ) : this() {
        this.bpk = bpk
        this.certificate = certificate
        this.revoked = false
        this.deviceName = deviceName
        this.deviceId = deviceId
        this.validUntil = validUntil
    }

    @Id
    @GeneratedValue
    var id: Long = 0

    @Column
    @CreationTimestamp
    lateinit var createdOn: Instant

    @Column
    lateinit var bpk: String

    @Column
    @Lob
    lateinit var certificate: ByteArray

    @Column
    lateinit var validUntil: Instant

    @Column
    var revoked: Boolean = false

    @Column
    lateinit var deviceName: String

    @Column
    lateinit var deviceId: String

    @OneToMany(mappedBy = "deviceBinding", cascade = [CascadeType.ALL])
    val issuedCredentialList: MutableList<IssuedCredential> = mutableListOf()

    override fun toString(): String {
        return "DeviceBinding(id=$id, " +
                "createdOn=$createdOn, " +
                "bpk='$bpk', " +
                "certificate=${certificate.encodeBase64()}, " +
                "validUntil=$validUntil, " +
                "revoked=$revoked, " +
                "deviceName='$deviceName', " +
                "deviceId='$deviceId', " +
                "issuedCredentialList=${issuedCredentialList.size})"
    }


}
