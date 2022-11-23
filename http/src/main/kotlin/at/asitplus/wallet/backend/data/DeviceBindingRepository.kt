package at.asitplus.wallet.backend.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface DeviceBindingRepository : JpaRepository<DeviceBinding, Long> {

    @Query("select d from DeviceBinding d where d.certificate = ?1 and d.validUntil > ?2")
    fun findByCertificateAndValidUntilAfter(
        certificate: ByteArray,
        validUntil: Instant
    ): DeviceBinding?

    fun findAllByBpkAndValidUntilAfter(bpk: String, validUntil: Instant): Collection<DeviceBinding>

    fun findAllByBpkAndDeviceIdAndValidUntilAfter(
        bpk: String,
        deviceId: String,
        validUntil: Instant
    ): Collection<DeviceBinding>

    fun findAllByValidUntilAfter(validUntil: Instant): Collection<DeviceBinding>

    fun findAllByValidUntilBefore(cutoff: Instant): Collection<DeviceBinding>

}