package at.asitplus.wallet.backend.service

import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.RevokedCredential
import kotlinx.datetime.Instant

/**
 * Service to store device bindings, that are created by the Wallet App,
 * and used later on for authentication.
 */
interface DeviceBindingStorageService {

    fun store(bpk: String, certificate: ByteArray, deviceName: String, validUntil: Instant): DeviceBinding

    fun lookupBpk(decodedCert: ByteArray): String?

    fun lookupDevices(bpk: String): Collection<DeviceListEntry>

    fun getDeviceBindingForCurrentUser(): DeviceBinding?

    fun revoke(bpk: String, deviceId: String?): Map<DeviceBinding,Collection<RevokedCredential>>

    fun deleteExpiredBefore(cutoff: Instant): Int

}

data class DeviceListEntry(
    val deviceName: String,
    val deviceId: String,
)

