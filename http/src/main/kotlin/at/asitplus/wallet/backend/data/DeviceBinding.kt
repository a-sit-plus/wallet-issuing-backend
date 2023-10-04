package at.asitplus.wallet.backend.data

import io.matthewnelson.component.base64.encodeBase64
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.Instant
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.OneToMany

/**
 * A non-revoked binding certificate
 */
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
    lateinit var deviceName: String

    @Column
    lateinit var deviceId: String

    @OneToMany(mappedBy = "deviceBinding", cascade = [CascadeType.ALL], orphanRemoval = true)
    val issuedCredentialList: MutableList<IssuedCredential> = mutableListOf()

    override fun toString(): String {
        return "DeviceBinding(id=$id, " +
                "createdOn=$createdOn, " +
                "bpk='$bpk', " +
                "certificate=${certificate.encodeToString(Base64())}, " +
                "validUntil=$validUntil, " +
                "deviceName='$deviceName', " +
                "deviceId='$deviceId', " +
                "issuedCredentialList=${issuedCredentialList.size})"
    }
}
