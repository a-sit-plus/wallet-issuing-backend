package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.lib.data.CredentialSubject
import at.asitplus.wallet.lib.encodeBase64
import org.slf4j.LoggerFactory
import java.time.Instant


interface RevocationService {

    /**
     * Revokes all credentials for one pupil, specified by their [bpk].
     */
    fun revokeCredentialsByBpk(bpk: String): Int

    /**
     * Revokes all credentials for one pupil, specified by their [bpk],
     * or specified by their [bpk] and [deviceId].
     */
    fun revokeCredentialsByBpkAndDeviceId(bpk: String, deviceId: String?): Int

    /**
     * Revokes one credential, specified by its [vcId] (which is a unique identifier
     * for one verifiable credential, in the form of `urn:uuid:${uuid4()}`)
     */
    fun revokeCredentialsByVcId(vcId: String): Int

    /**
     * Stores the verifiable credential that is about to be issued,
     * and returns the [IssuedCredential.revocationListIndex] for this credential,
     * that will be included in the verifiable credentials (so that consumers
     * can verify the revocation status).
     */
    fun storeGetNextIndex(
        vcId: String,
        credentialSubject: CredentialSubject,
        issuanceDate: Instant,
        expirationDate: Instant
    ): Int?

    /**
     * Checks whether a credential with [vcId] is revoked. May return null, if the [vcId] is unknown.
     */
    fun isRevoked(vcId: String): Boolean?

    /**
     * Lists all non-revoked credentials that have been issued
     */
    fun getAllNonRevokedWithDetails(): Collection<IssuedCredential>

    /**
     * Lists the field [IssuedCredential.revocationListIndex] for all credentials that have been revoked.
     */
    fun getRevokedStatusListIndexList(): Collection<Int>

    /**
     * Revoke one or more device bindings for a pupil, either specified by their [bpk]
     * or specified by their [bpk] and [deviceId].
     * This will also revoke all issued credentials for that binding.
     */
    fun revokeBinding(bpk: String, deviceId: String?): Int

    /**
     * Deletes all issued credentials that are not valid on the [cutoff] date any more.
     */
    fun deleteExpiredCredentialsBefore(cutoff: Instant): Int

}

class DefaultRevocationService(
    private val credentialRepo: IssuedCredentialRepository,
    private val deviceBindingStorageService: DeviceBindingStorageService,
    private val oneCredentialPerDeviceBinding: Boolean,
    private val pkiService: PkiService,
) : RevocationService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * Checks whether a credential with [vcId] is revoked. May return null, if the [vcId] is unknown.
     */
    override fun isRevoked(vcId: String): Boolean? {
        return credentialRepo.findByVcId(vcId)?.revoked
    }

    /**
     * Stores the verifiable credential that is about to be issued,
     * and returns the [IssuedCredential.revocationListIndex] for this credential,
     * that will be included in the verifiable credentials (so that consumers
     * can verify the revocation status).
     */
    override fun storeGetNextIndex(
        vcId: String,
        credentialSubject: CredentialSubject,
        issuanceDate: Instant,
        expirationDate: Instant
    ): Int? {
        if (credentialRepo.findByVcId(vcId) != null)
            return null.also {
                log.error("Tried to store a new credential for existing vcId '{}'", vcId)
            }
        val deviceBinding = deviceBindingStorageService.getDeviceBindingForCurrentUser()
            ?: return null.also {
                log.error("Got no authenticated user when trying to store vcId '{}'", vcId)
            }
        if (oneCredentialPerDeviceBinding) {
            val revokedCreds = revokeAllCredentials(deviceBinding.issuedCredentialList)
            if (revokedCreds > 0)
                log.info(
                    "Revoked {} already existing credentials for device binding certificate '{}' for bpk '{}'",
                    revokedCreds, deviceBinding.certificate.encodeBase64(), deviceBinding.bpk
                )
        }
        val revocationListIndex = (credentialRepo.getMaxRevocationListIndex() ?: 0) + 1
        val attributeName = credentialSubject.javaClass.simpleName
        val issuedCredential = IssuedCredential(
            vcId,
            credentialSubject.id,
            expirationDate,
            deviceBinding,
            attributeName,
            revocationListIndex
        )
        val savedCredential = credentialRepo.save(issuedCredential)
        return savedCredential.revocationListIndex.toInt()
    }

    /**
     * Revokes one credential, specified by its [vcId] (which is a unique identifier
     * for one verifiable credential, in the form of `urn:uuid:${uuid4()}`)
     */
    override fun revokeCredentialsByVcId(vcId: String): Int {
        val credential = credentialRepo.findByVcId(vcId) ?: return 0
        return revokeAllCredentials(listOf(credential))
    }

    /**
     * Revokes all credentials for one pupil, specified by their [bpk].
     */
    override fun revokeCredentialsByBpk(bpk: String): Int {
        val credentials = credentialRepo.findByRevokedFalseAndValidUntilAfterAndDeviceBinding_Bpk(Instant.now(), bpk)
        return revokeAllCredentials(credentials)
    }

    /**
     * Revokes all credentials for one pupil, specified by their [bpk],
     * or specified by their [bpk] and [deviceId].
     */
    override fun revokeCredentialsByBpkAndDeviceId(bpk: String, deviceId: String?): Int {
        if (deviceId == null)
            return revokeCredentialsByBpk(bpk)
        val credentials =
            credentialRepo.findByRevokedFalseAndValidUntilAfterAndDeviceBinding_BpkAndDeviceBinding_DeviceId(
                Instant.now(), bpk, deviceId
            )
        return revokeAllCredentials(credentials)
    }

    /**
     * Revoke one or more device bindings for a pupil, either specified by their [bpk]
     * or specified by their [bpk] and [deviceId].
     * This will also revoke all issued credentials for that binding.
     */
    override fun revokeBinding(bpk: String, deviceId: String?): Int {
        val revokedBindings = deviceBindingStorageService.revoke(bpk, deviceId)
        if (revokedBindings.isEmpty())
            return 0
        revokedBindings.forEach { pkiService.revokeCertificate(it.certificate) }
        return revokeCredentialsByBpkAndDeviceId(bpk, deviceId)
    }

    private fun revokeAllCredentials(credentials: Collection<IssuedCredential>): Int {
        if (credentials.isEmpty())
            return 0
        val toRevoke = credentials.filter { !it.revoked }
        toRevoke.forEach { it.revoked = true }
        credentialRepo.saveAllAndFlush(toRevoke)
        return toRevoke.count()
    }

    /**
     * Lists the field [IssuedCredential.revocationListIndex] for all credentials that have been revoked.
     */
    override fun getRevokedStatusListIndexList(): Collection<Int> {
        return credentialRepo.getRevocationListIndexByRevokedTrueOrdered().map { it.toInt() }
    }

    /**
     * Lists all non-revoked credentials that have been issued
     */
    override fun getAllNonRevokedWithDetails(): Collection<IssuedCredential> {
        return credentialRepo.findAllByRevokedFalseAndValidUntilAfter(Instant.now())
    }

    /**
     * Deletes all issued credentials that are not valid on the [cutoff] date any more.
     */
    override fun deleteExpiredCredentialsBefore(cutoff: Instant): Int {
        val list = credentialRepo.findAllByValidUntilBefore(cutoff)
        list.forEach {
            log.info("Deleting credential: {} for {} (bpk '{}')", it.vcId, it.subjectId, it.deviceBinding.bpk)
            credentialRepo.delete(it)
        }
        return list.size
    }
}