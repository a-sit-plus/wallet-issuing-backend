package at.asitplus.wallet.backend.data

import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.DeviceListEntry
import at.asitplus.wallet.backend.auth.AuthenticatedDeviceBindingUser
import at.asitplus.wallet.lib.encodeBase64
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class DatabaseDeviceBindingStorageService(
    private val deviceBindingRepository: DeviceBindingRepository,
) : DeviceBindingStorageService {

    private val log = LoggerFactory.getLogger(this.javaClass)

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

    override fun getDeviceBindingForCurrentUser(): DeviceBinding? {
        val principal = SecurityContextHolder.getContext()?.authentication?.principal
        if (principal !is AuthenticatedDeviceBindingUser)
            return null.also {
                log.error("Got no authenticated user when trying to store vc")
            }
        return deviceBindingRepository.findByCertificate(principal.certificate)
            ?: return null.also {
                log.error("Found no authenticated user for certificate '${principal.certificate.encodeBase64()}'")
            }
    }

}