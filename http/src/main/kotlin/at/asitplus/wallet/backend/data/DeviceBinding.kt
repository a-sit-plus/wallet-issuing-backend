package at.asitplus.wallet.backend.data

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

    constructor(bpk: String, certificate: ByteArray, deviceName: String, deviceId: String) : this() {
        this.bpk = bpk
        this.certificate = certificate
        this.revoked = false
        this.deviceName = deviceName
        this.deviceId = deviceId
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
    var revoked: Boolean = false

    @Column
    lateinit var deviceName: String

    @Column
    lateinit var deviceId: String

    @OneToMany(mappedBy = "deviceBinding", cascade = [CascadeType.ALL])
    val issuedCredentialList: MutableList<IssuedCredential> = mutableListOf()

}
