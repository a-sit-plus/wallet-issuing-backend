package at.asitplus.wallet.backend.data

import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.backend.service.DeviceBindingStorageService
import at.asitplus.wallet.backend.service.DeviceListEntry
import at.asitplus.wallet.lib.encodeBase64
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.slf4j.LoggerFactory
import java.util.*


class DatabaseDeviceBindingStorageService(
    private val deviceBindingRepository: DeviceBindingRepository,
    private val revokedCredentialRepo: RevokedCredentialRepository,
    private val authenticationSupplier: AuthenticationSupplier,
    private val clock: Clock
) : DeviceBindingStorageService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun store(
        bpk: String,
        certificate: ByteArray,
        deviceName: String,
        validUntil: Instant
    ): DeviceBinding {
        log.info("Storing device binding for '{}' and '{}'", bpk, deviceName)
        return DeviceBinding(
            bpk = bpk,
            certificate = certificate,
            deviceName = deviceName,
            deviceId = UUID.randomUUID().toString(),
            validUntil = validUntil.toJavaInstant()
        ).also {
            deviceBindingRepository.save(it)
        }
    }

    override fun lookupBpk(decodedCert: ByteArray): String? {
        return deviceBindingRepository
            .findByCertificateAndValidUntilAfter(decodedCert, clock.now().toJavaInstant())?.bpk
    }

    override fun lookupDevices(bpk: String): Collection<DeviceListEntry> {
        return deviceBindingRepository
            .findAllByBpkAndValidUntilAfter(bpk, clock.now().toJavaInstant())
            .map { DeviceListEntry(it.deviceName, it.deviceId) }
    }

    override fun getDeviceBindingForCurrentUser(): DeviceBinding? {
        val certificate = authenticationSupplier.getCurrentUserCertificate()
            ?: return null.also {
                log.error("Got no authenticated user when trying to store vc")
            }
        val now = clock.now().toJavaInstant()
        return deviceBindingRepository
            .findByCertificateAndValidUntilAfter(certificate, now)
            ?: return null.also {
                log.error(
                    "Found no authenticated user at $now for certificate '{}",
                    certificate.encodeBase64()
                )
            }
    }

    override fun revoke(
        bpk: String,
        deviceId: String?
    ): Map<DeviceBinding, Collection<RevokedCredential>> {
        synchronized(CredentialRepositoriesLock) {
            val toRevoke = if (deviceId != null)
                deviceBindingRepository
                    .findAllByBpkAndDeviceIdAndValidUntilAfter(
                        bpk,
                        deviceId,
                        clock.now().toJavaInstant()
                    )
            else
                deviceBindingRepository
                    .findAllByBpkAndValidUntilAfter(bpk, clock.now().toJavaInstant())

            log.info("Revoking {} device bindings", toRevoke.size)

            val revoked = toRevoke.associateWith { binding ->
                binding.issuedCredentialList.map {
                    RevokedCredential(it.timePeriod, it.revocationListIndex)
                }.also {
                    binding.issuedCredentialList.clear()
                    revokedCredentialRepo.saveAll(it)
                }
            }

            deviceBindingRepository.saveAll(toRevoke)
            deviceBindingRepository.deleteAllInBatch(toRevoke)
            return revoked
        }
    }

    override fun deleteExpiredBefore(cutoff: Instant): Int {
        val list = deviceBindingRepository.findAllByValidUntilBefore(cutoff.toJavaInstant())
        list.forEach {
            log.info("Deleting device binding: '{}' for '{}'", it.deviceName, it.bpk)
            deviceBindingRepository.delete(it)
        }
        return list.size
    }
}