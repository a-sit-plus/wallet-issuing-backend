package at.asitplus.wallet.backend.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DeviceBindingRepository : JpaRepository<DeviceBinding, Long> {

    fun findByCertificateAndRevokedIsFalse(certificate: ByteArray): DeviceBinding?

    fun findAllByBpkAndRevokedIsFalse(bpk: String): Collection<DeviceBinding>

    fun findAllByBpkAndDeviceIdAndRevokedIsFalse(bpk: String, deviceId: String): Collection<DeviceBinding>

}