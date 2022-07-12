package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.DeviceListEntry
import at.asitplus.wallet.backend.data.DeviceBinding
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
        DeviceBinding(bpk, certificate, deviceName, UUID.randomUUID().toString(), validUntil.toJavaInstant()).also {
            list += it
        }

    override fun lookupBpk(decodedCert: ByteArray) =
        list.firstOrNull { it.certificate.contentEquals(decodedCert) }?.bpk

    override fun lookupDevices(bpk: String) =
        list.filter { it.bpk == bpk }.map { DeviceListEntry(it.deviceName, it.deviceId) }

    override fun getDeviceBindingForCurrentUser() = list.firstOrNull()

    override fun revoke(bpk: String, deviceId: String?): Collection<DeviceBinding> {
        val toRevoke = list.filter { it.bpk == bpk }
            .filter { if (deviceId != null) it.deviceId == deviceId else true }
            .filter { !it.revoked }
        toRevoke.forEach { it.revoked = true }
        return toRevoke.toList()
    }

    override fun deleteExpiredBefore(cutoff: Instant): Int {
        val toRemove = list.filter { cutoff > it.validUntil.toKotlinInstant() }
        list.removeAll(toRemove)
        return toRemove.size
    }
}