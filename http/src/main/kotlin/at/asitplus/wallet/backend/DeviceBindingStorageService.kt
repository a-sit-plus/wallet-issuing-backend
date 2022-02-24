package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.data.DeviceBinding

interface DeviceBindingStorageService {

    fun store(bpk: String, certificate: ByteArray, deviceName: String): DeviceBinding

    fun lookupBpk(decodedCert: ByteArray): String?

    fun lookupDevices(bpk: String): Collection<DeviceListEntry>

    fun getDeviceBindingForCurrentUser(): DeviceBinding?

}

data class DeviceListEntry(
    val deviceName: String,
    val deviceId: String,
)

