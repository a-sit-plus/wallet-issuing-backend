package at.asitplus.wallet.backend.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DeviceBindingRepository : JpaRepository<DeviceBinding, Long> {

    fun findByCertificate(certificate: ByteArray): DeviceBinding?

    fun findAllByBpk(bpk: String): Collection<DeviceBinding>?

}