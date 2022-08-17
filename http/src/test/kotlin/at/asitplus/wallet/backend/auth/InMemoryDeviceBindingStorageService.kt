package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.RevokedCredential
import at.asitplus.wallet.backend.service.DeviceBindingStorageService
import at.asitplus.wallet.backend.service.DeviceListEntry
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import java.util.*

class InMemoryDeviceBindingStorageService : DeviceBindingStorageService {

    private val list = mutableListOf<DeviceBinding>()

    override fun store(
        bpk: String,
        certificate: ByteArray,
        deviceName: String,
        validUntil: Instant
    ) =
        DeviceBinding(
            bpk,
            certificate,
            deviceName,
            UUID.randomUUID().toString(),
            validUntil.toJavaInstant()
        ).also {
            list += it
        }

    override fun lookupBpk(decodedCert: ByteArray) =
        list.firstOrNull { it.certificate.contentEquals(decodedCert) }?.bpk

    override fun lookupDevices(bpk: String) =
        list.filter { it.bpk == bpk }.map { DeviceListEntry(it.deviceName, it.deviceId) }

    override fun getDeviceBindingForCurrentUser() = list.firstOrNull()

    override fun revoke(
        bpk: String,
        deviceId: String?
    ): Map<DeviceBinding, Collection<RevokedCredential>> {
        val toRevoke = list.filter { it.bpk == bpk }
            .filter { if (deviceId != null) it.deviceId == deviceId else true }
        list.removeAll(toRevoke)
        return toRevoke.associateWith { binding ->
            binding.issuedCredentialList.map {
                RevokedCredential(
                    it.timePeriod,
                    it.revocationListIndex
                )
            }.also { binding.issuedCredentialList.clear() }
        }
    }

    override fun deleteExpiredBefore(cutoff: Instant): Int {
        val toRemove = list.filter { cutoff > it.validUntil.toKotlinInstant() }
        list.removeAll(toRemove)
        return toRemove.size
    }
}