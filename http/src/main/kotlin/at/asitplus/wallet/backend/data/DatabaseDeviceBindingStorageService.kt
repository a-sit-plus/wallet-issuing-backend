package at.asitplus.wallet.backend.data

import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.DeviceListEntry
import at.asitplus.wallet.backend.auth.AuthenticatedDeviceBindingUser
import at.asitplus.wallet.lib.encodeBase64
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.UUID

class DatabaseDeviceBindingStorageService(
    private val deviceBindingRepository: DeviceBindingRepository,
) : DeviceBindingStorageService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun store(bpk: String, certificate: ByteArray, deviceName: String): DeviceBinding {
        log.info("Storing device binding for '{}' and '{}'", bpk, deviceName)
        return DeviceBinding(
            bpk = bpk,
            certificate = certificate,
            deviceName = deviceName,
            deviceId = UUID.randomUUID().toString()
        ).also {
            deviceBindingRepository.save(it)
        }
    }

    override fun lookupBpk(decodedCert: ByteArray): String? {
        return deviceBindingRepository.findByCertificateAndRevokedIsFalse(decodedCert)?.bpk
    }

    override fun lookupDevices(bpk: String): Collection<DeviceListEntry> {
        return deviceBindingRepository.findAllByBpkAndRevokedIsFalse(bpk)
            .map { DeviceListEntry(it.deviceName, it.deviceId) }
    }

    override fun getDeviceBindingForCurrentUser(): DeviceBinding? {
        val principal = SecurityContextHolder.getContext()?.authentication?.principal
        if (principal !is AuthenticatedDeviceBindingUser)
            return null.also {
                log.error("Got no authenticated user when trying to store vc")
            }
        return deviceBindingRepository.findByCertificateAndRevokedIsFalse(principal.certificate)
            ?: return null.also {
                log.error("Found no authenticated user for certificate '{}", principal.certificate.encodeBase64())
            }
    }

    override fun revoke(bpk: String, deviceId: String?): Collection<DeviceBinding> {
        val list = if (deviceId != null)
            deviceBindingRepository.findAllByBpkAndDeviceIdAndRevokedIsFalse(bpk, deviceId)
        else
            deviceBindingRepository.findAllByBpkAndRevokedIsFalse(bpk)
        val toRevoke = list.filter { !it.revoked }
        toRevoke.forEach { it.revoked = true }
        log.info("Revoking {} device bindings", toRevoke.size)
        return deviceBindingRepository.saveAll(toRevoke)
    }

    override fun deleteExpiredBefore(cutoff: Instant): Int {
        val list = deviceBindingRepository.findAllByValidUntilBefore(cutoff)
        list.forEach {
            log.info("Deleting device binding: '{}' for '{}'", it.deviceName, it.bpk)
            deviceBindingRepository.delete(it)
        }
        return list.size
    }
}