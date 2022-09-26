package at.asitplus.wallet.backend.service

import at.asitplus.wallet.backend.data.*
import at.asitplus.wallet.backend.pki.PkiService
import at.asitplus.wallet.lib.data.CredentialSubject
import at.asitplus.wallet.lib.encodeBase64
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.slf4j.LoggerFactory
import java.lang.Long.max


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
    fun revokeCredentialsByVcId(vcId: String, timePeriod: Int): Int

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
        expirationDate: Instant,
        timePeriod: Int
    ): Long?

    /**
     * Checks whether a credential with [vcId] is revoked. May return null, if the [vcId] is unknown.
     */
    fun isRevoked(vcId: String, timePeriod: Int): Boolean?

    /**
     * Lists all non-revoked credentials that have been issued
     */
    fun getAllNonRevokedWithDetails(): Collection<IssuedCredential>

    /**
     * Lists the field [IssuedCredential.revocationListIndex] for all credentials that have been revoked.
     */
    fun getRevokedStatusListIndexList(timePeriod: Int): Collection<Long>

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
    private val revokedCredentialRepo: RevokedCredentialRepository,
    private val deviceBindingStorageService: DeviceBindingStorageService,
    private val oneCredentialPerDeviceBinding: Boolean,
    private val pkiService: PkiService,
    private val clock: Clock
) : RevocationService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * Checks whether a credential with [vcId] is revoked i.e. whether it exists or not
     *
     */
    override fun isRevoked(vcId: String, timePeriod: Int): Boolean {
        return credentialRepo.findBytimePeriodAndVcId(timePeriod, vcId) == null
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
        expirationDate: Instant,
        timePeriod: Int
    ): Long? {
        synchronized(repoLock) {
            if (credentialRepo.findBytimePeriodAndVcId(timePeriod, vcId) != null)
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
            val revocationListIndex = max(
                (credentialRepo.getMaxRevocationListIndex() ?: 0),
                revokedCredentialRepo.getMaxRevocationListIndex() ?: 0
            ) + 1
            val attributeName = credentialSubject.javaClass.simpleName
            val issuedCredential = IssuedCredential(
                vcId,
                credentialSubject.id,
                expirationDate.toJavaInstant(),
                timePeriod,
                deviceBinding,
                attributeName,
                revocationListIndex
            )
            val savedCredential = credentialRepo.save(issuedCredential)
            return savedCredential.revocationListIndex
        }
    }

    /**
     * Revokes one credential, specified by its [vcId] (which is a unique identifier
     * for one verifiable credential, in the form of `urn:uuid:${uuid4()}`)
     */
    override fun revokeCredentialsByVcId(vcId: String, timePeriod: Int): Int {
        val credential =
            credentialRepo.findBytimePeriodAndVcId(timePeriod, vcId) ?: return 0
        return revokeAllCredentials(listOf(credential))
    }

    /**
     * Revokes all credentials for one pupil, specified by their [bpk].
     */
    override fun revokeCredentialsByBpk(bpk: String): Int {
        val credentials = credentialRepo.findByValidUntilAfterAndDeviceBinding_Bpk(
            clock.now().toJavaInstant(),
            bpk
        )
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
            credentialRepo.findByValidUntilAfterAndDeviceBinding_BpkAndDeviceBinding_DeviceId(
                clock.now().toJavaInstant(), bpk, deviceId
            )
        return revokeAllCredentials(credentials)
    }

    /**
     * Revoke one or more device bindings for a pupil, either specified by their [bpk]
     * or specified by their [bpk] and [deviceId].
     * This will also revoke all issued credentials for that binding.
     */
    override fun revokeBinding(bpk: String, deviceId: String?): Int =
        deviceBindingStorageService.revoke(bpk, deviceId).map { it.value.count() }.sum()

    private fun revokeAllCredentials(toRevoke: Collection<IssuedCredential>): Int {
        synchronized(repoLock) {
            revokedCredentialRepo.saveAll(toRevoke.map { RevokedCredential(it.timePeriod, it.revocationListIndex) })
            credentialRepo.deleteAllInBatch(toRevoke)
            return toRevoke.count()
        }
    }

    /**
     * Lists the field [IssuedCredential.revocationListIndex] for all credentials that have been revoked.
     */
    override fun getRevokedStatusListIndexList(timePeriod: Int): Collection<Long> {
        return revokedCredentialRepo.getRevocationListIndexByTimePeriod(timePeriod)
            .map { it }
    }

    /**
     * Lists all non-revoked credentials that have been issued
     */
    override fun getAllNonRevokedWithDetails(): Collection<IssuedCredential> {
        return credentialRepo.findAllByValidUntilAfter(clock.now().toJavaInstant())
    }

    /**
     * Deletes all issued credentials that are not valid on the [cutoff] date any more.
     */
    override fun deleteExpiredCredentialsBefore(cutoff: Instant): Int {
        val list = credentialRepo.findAllByValidUntilBefore(cutoff.toJavaInstant())
        list.forEach {
            log.info(
                "Deleting credential: {} for {} (bpk '{}')",
                it.vcId,
                it.subjectId,
                it.deviceBinding.bpk
            )
            credentialRepo.delete(it)
        }
        return list.size
    }
}