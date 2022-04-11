package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.lib.data.CredentialSubject
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory


interface RevocationService {

    fun revokeCredentialsByBpk(bpk: String): Int

    fun revokeCredentialsByBpkAndDeviceId(bpk: String, deviceId: String?): Int

    fun revokeCredentialsByVcId(vcId: String): Int

    fun storeGetNextIndex(
        vcId: String,
        credentialSubject: CredentialSubject,
        issuanceDate: Instant,
        expirationDate: Instant
    ): Int?

    fun isRevoked(vcId: String): Boolean?

    fun getAllNonRevokedWithDetails(): Collection<IssuedCredential>

    fun getRevokedStatusListIndexList(): Collection<Int>
}

class DefaultRevocationService(
    private val credentialRepo: IssuedCredentialRepository,
    private val deviceBindingStorageService: DeviceBindingStorageService,
) : RevocationService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun isRevoked(vcId: String): Boolean? {
        return credentialRepo.findByVcId(vcId)?.revoked
    }

    override fun storeGetNextIndex(
        vcId: String,
        credentialSubject: CredentialSubject,
        issuanceDate: Instant,
        expirationDate: Instant
    ): Int? {
        if (credentialRepo.findByVcId(vcId) != null)
            return null.also {
                log.error("Tried to store a new credential for existing vcId '$vcId'")
            }
        val deviceBinding = deviceBindingStorageService.getDeviceBindingForCurrentUser()
            ?: return null.also {
                log.error("Got no authenticated user when trying to store vcId '$vcId'")
            }
        val exp = java.time.Instant.ofEpochMilli(expirationDate.toEpochMilliseconds())
        val issuedCredential =
            IssuedCredential(vcId, credentialSubject.id, exp, deviceBinding, credentialSubject.javaClass.simpleName)
        val savedCredential = credentialRepo.save(issuedCredential)
        return savedCredential.revocationListIndex.toInt()
    }

    override fun revokeCredentialsByVcId(vcId: String): Int {
        val credential = credentialRepo.findByVcId(vcId) ?: return 0
        return revokeAllCredentials(listOf(credential))
    }

    override fun revokeCredentialsByBpk(bpk: String): Int {
        val credentials = credentialRepo.findByRevokedFalseAndDeviceBinding_Bpk(bpk)
        return revokeAllCredentials(credentials)
    }

    override fun revokeCredentialsByBpkAndDeviceId(bpk: String, deviceId: String?): Int {
        if (deviceId == null)
            return revokeCredentialsByBpk(bpk)
        val credentials = credentialRepo.findByRevokedFalseAndDeviceBinding_BpkAndDeviceBinding_DeviceId(bpk, deviceId)
        return revokeAllCredentials(credentials)
    }

    private fun revokeAllCredentials(credentials: Collection<IssuedCredential>): Int {
        if (credentials.isEmpty())
            return 0
        val toStore = mutableListOf<IssuedCredential>()
        credentials.forEach {
            it.revoked = true
            toStore.add(it)
        }
        toStore.forEach {
            credentialRepo.save(it)
        }
        return toStore.count()
    }

    override fun getRevokedStatusListIndexList(): Collection<Int> {
        return credentialRepo.findAllByRevokedTrueOrderByRevocationListIndex()
            .map { it.revocationListIndex.toInt() }
    }

    override fun getAllNonRevokedWithDetails(): Collection<IssuedCredential> {
        return credentialRepo.findAllByRevokedFalse()
    }
}