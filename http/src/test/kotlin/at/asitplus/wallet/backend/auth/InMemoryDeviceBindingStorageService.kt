package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.DeviceListEntry
import at.asitplus.wallet.backend.data.DeviceBinding
import java.time.Instant
import java.util.UUID

class InMemoryDeviceBindingStorageService : DeviceBindingStorageService {

    private val list = mutableListOf<DeviceBinding>()
    private var deviceBindingForCurrentUser: DeviceBinding? = null

    override fun store(bpk: String, certificate: ByteArray, deviceName: String, validUntil: Instant) =
        DeviceBinding(bpk, certificate, deviceName, UUID.randomUUID().toString(), validUntil).also {
            list += it
        }

    override fun lookupBpk(decodedCert: ByteArray) =
        list.firstOrNull { it.certificate.contentEquals(decodedCert) }?.bpk

    override fun lookupDevices(bpk: String) =
        list.filter { it.bpk == bpk }.map { DeviceListEntry(it.deviceName, it.deviceId) }

    override fun getDeviceBindingForCurrentUser() = deviceBindingForCurrentUser

    override fun revoke(bpk: String, deviceId: String?): Collection<DeviceBinding> {
        val toRevoke = list.filter { it.bpk == bpk }
            .filter { if (deviceId != null) it.deviceId == deviceId else true }
            .filter { !it.revoked }
        toRevoke.forEach { it.revoked = true }
        return toRevoke.toList()
    }

    override fun deleteExpiredBefore(cutoff: Instant): Int {
        val toRemove = list.filter {
            cutoff.isAfter(it.validUntil)
        }
        list.removeAll(toRemove)
        return toRemove.size
    }
}