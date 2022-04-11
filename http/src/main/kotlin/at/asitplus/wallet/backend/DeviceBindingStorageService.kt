package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.data.DeviceBinding

/**
 * Service to store device bindings, that are created by the Wallet App,
 * and used later on for authentication.
 */
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

