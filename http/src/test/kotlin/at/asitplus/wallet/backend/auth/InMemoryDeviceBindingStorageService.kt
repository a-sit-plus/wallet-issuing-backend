package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.DeviceListEntry
import at.asitplus.wallet.backend.data.DeviceBinding
import java.util.UUID

class InMemoryDeviceBindingStorageService : DeviceBindingStorageService {

    private val list = mutableListOf<DeviceBinding>()
    private var deviceBindingForCurrentUser: DeviceBinding? = null

    override fun store(bpk: String, certificate: ByteArray, deviceName: String): DeviceBinding {
        return DeviceBinding(bpk, certificate, deviceName, UUID.randomUUID().toString()).also {
            list += it
        }
    }

    override fun lookupBpk(decodedCert: ByteArray): String? {
        return list.firstOrNull { it.certificate.contentEquals(decodedCert) }?.bpk
    }

    override fun lookupDevices(bpk: String): Collection<DeviceListEntry> {
        return list.filter { it.bpk == bpk }.map { DeviceListEntry(it.deviceName, it.deviceId) }
    }

    override fun getDeviceBindingForCurrentUser(): DeviceBinding? {
        return deviceBindingForCurrentUser
    }

    fun setDeviceBindingForCurrentUser(deviceBinding: DeviceBinding) {
        list += deviceBinding
        this.deviceBindingForCurrentUser = deviceBinding
    }

}