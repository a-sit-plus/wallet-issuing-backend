package at.asitplus.wallet.backend.data

import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.backend.service.DeviceBindingStorageService
import at.asitplus.wallet.backend.service.DeviceListEntry
import at.asitplus.wallet.backend.service.RevocationEvent
import io.github.aakira.napier.Napier
import io.matthewnelson.component.base64.encodeBase64
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID


class DatabaseDeviceBindingStorageService(
    private val deviceBindingRepository: DeviceBindingRepository,
    private val revokedCredentialRepo: RevokedCredentialRepository,
    private val authenticationSupplier: AuthenticationSupplier,
    private val applicationEventPublisher: ApplicationEventPublisher,
) : DeviceBindingStorageService {

    override fun store(
        bpk: String,
        certificate: ByteArray,
        deviceName: String,
        validUntil: Instant
    ): DeviceBinding {
        Napier.i("Storing device binding")
        Napier.v("bpk: $bpk, deviceName: $deviceName")
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
            .findByCertificateAndValidUntilAfter(decodedCert, java.time.Instant.now())?.bpk
    }

    override fun lookupDevices(bpk: String): Collection<DeviceListEntry> {
        return deviceBindingRepository
            .findAllByBpkAndValidUntilAfter(bpk, java.time.Instant.now())
            .map { DeviceListEntry(it.deviceName, it.deviceId) }
    }

    override fun getDeviceBindingForCurrentUser(): DeviceBinding? {
        val certificate = authenticationSupplier.getCurrentUserCertificate()
            ?: return null.also {
                Napier.e("Got no authenticated user when trying to store vc")
            }
        val now = java.time.Instant.now()
        return deviceBindingRepository
            .findByCertificateAndValidUntilAfter(certificate, now)
            ?: return null.also {
                Napier.e("Found no authenticated user at $now for given certificate")
                Napier.v("Certificate: ${certificate.encodeToString(Base64())}")
            }
    }

    override fun revoke(
        bpk: String,
        deviceId: String?
    ): Map<DeviceBinding, Collection<RevokedCredential>> {
        synchronized(CredentialRepositoriesLock) {
            val toRevoke = if (deviceId != null) {
                deviceBindingRepository
                    .findAllByBpkAndDeviceIdAndValidUntilAfter(bpk, deviceId, java.time.Instant.now())
            } else {
                deviceBindingRepository.findAllByBpkAndValidUntilAfter(bpk, java.time.Instant.now())
            }
            Napier.i("Revoking ${toRevoke.size} device bindings")

            val revoked = toRevoke.associateWith { binding ->
                binding.issuedCredentialList.map {
                    RevokedCredential(it.timePeriod, it.revocationListIndex)
                }.also {
                    binding.issuedCredentialList.clear()
                    revokedCredentialRepo.saveAll(it)
                    it.forEach { cred ->
                        applicationEventPublisher.publishEvent(RevocationEvent(this, cred.timePeriod))
                    }
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
            Napier.i("Deleting device binding")
            Napier.v("DeviceName: ${it.deviceName}, bpk: ${it.bpk}")
            deviceBindingRepository.delete(it)
        }
        return list.size
    }
}