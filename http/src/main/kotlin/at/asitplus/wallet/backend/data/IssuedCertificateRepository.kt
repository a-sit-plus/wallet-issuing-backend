package at.asitplus.wallet.backend.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface IssuedCertificateRepository : JpaRepository<IssuedCertificate, Long> {

    fun findBySerialNumber(serialNumber: Long): IssuedCertificate?

    fun findAllByRevokedTrueAndValidFromBeforeAndValidUntilAfter(
        validFrom: Instant,
        validUntil: Instant
    ): Collection<IssuedCertificate>

    fun findByCertificate(certificate: ByteArray): IssuedCertificate?

}