package at.asitplus.wallet.backend.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface DeviceBindingRepository : JpaRepository<DeviceBinding, Long> {

    fun findByCertificateAndValidUntilAfterAndRevokedIsFalse(
        certificate: ByteArray,
        validUntil: Instant
    ): DeviceBinding?

    fun findAllByBpkAndValidUntilAfterAndRevokedIsFalse(bpk: String, validUntil: Instant): Collection<DeviceBinding>

    fun findAllByBpkAndDeviceIdAndValidUntilAfterAndRevokedIsFalse(
        bpk: String,
        deviceId: String,
        validUntil: Instant
    ): Collection<DeviceBinding>

    fun findAllByRevokedFalseAndValidUntilAfter(validUntil: Instant): Collection<DeviceBinding>

    fun findAllByValidUntilBefore(cutoff: Instant): Collection<DeviceBinding>

}