package at.asitplus.wallet.backend.data

import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.DeviceListEntry
import java.util.UUID

class DatabaseDeviceBindingStorageService(
    private val deviceBindingRepository: DeviceBindingRepository,
) : DeviceBindingStorageService {

    override fun store(bpk: String, certificate: ByteArray, deviceName: String) {
        deviceBindingRepository.save(
            DeviceBinding(
                bpk = bpk,
                certificate = certificate,
                deviceName = deviceName,
                deviceId = UUID.randomUUID().toString()
            )
        )
    }

    override fun lookupBpk(decodedCert: ByteArray): String? {
        return deviceBindingRepository.findByCertificate(decodedCert)?.bpk
    }

    override fun lookupDevices(bpk: String): Collection<DeviceListEntry>? {
        return deviceBindingRepository.findAllByBpk(bpk)
            ?.map { DeviceListEntry(it.deviceName, it.deviceId) }
    }
}