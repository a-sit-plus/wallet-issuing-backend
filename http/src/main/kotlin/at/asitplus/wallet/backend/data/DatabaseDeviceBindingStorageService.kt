package at.asitplus.wallet.backend.data

import at.asitplus.wallet.backend.DeviceBindingStorageService
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

}