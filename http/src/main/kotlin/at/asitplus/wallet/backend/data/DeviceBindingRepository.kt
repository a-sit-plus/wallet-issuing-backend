package at.asitplus.wallet.backend.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface DeviceBindingRepository : JpaRepository<DeviceBinding, Long> {

    fun findByCertificateAndRevokedIsFalse(certificate: ByteArray): DeviceBinding?

    fun findAllByBpkAndRevokedIsFalse(bpk: String): Collection<DeviceBinding>

    fun findAllByBpkAndDeviceIdAndRevokedIsFalse(bpk: String, deviceId: String): Collection<DeviceBinding>

    fun findAllByRevokedFalse(): Collection<DeviceBinding>

    fun findAllByValidUntilBefore(cutoff: Instant): Collection<DeviceBinding>

}