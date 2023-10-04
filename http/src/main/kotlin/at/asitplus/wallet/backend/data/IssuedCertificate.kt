package at.asitplus.wallet.backend.data

import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Lob

@Entity
class IssuedCertificate() {

    constructor(
        subject: String,
        issuer: String,
        validFrom: Instant,
        validUntil: Instant,
        serialNumber: Long,
        certificate: ByteArray,
    ) : this() {
        this.subject = subject
        this.issuer = issuer
        this.validFrom = validFrom
        this.validUntil = validUntil
        this.serialNumber = serialNumber
        this.certificate = certificate
    }

    @Id
    @GeneratedValue
    var id: Long = 0

    @Column
    @CreationTimestamp
    lateinit var createdOn: Instant

    @Column
    lateinit var subject: String

    @Column
    lateinit var issuer: String

    @Column
    lateinit var validFrom: Instant

    @Column
    lateinit var validUntil: Instant

    @Column
    var serialNumber: Long = 0

    @Column
    @Lob
    lateinit var certificate: ByteArray

    @Column
    var revoked: Boolean = false

    @Column
    var revocationDate: Instant? = null

}
