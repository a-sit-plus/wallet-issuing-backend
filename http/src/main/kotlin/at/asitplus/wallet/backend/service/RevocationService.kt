package at.asitplus.wallet.backend.service

import at.asitplus.wallet.backend.data.CredentialRepositoriesLock
import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.data.RevokedCredential
import at.asitplus.wallet.backend.data.RevokedCredentialRepository
import at.asitplus.wallet.lib.data.CredentialSubject
import io.github.aakira.napier.Napier
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import java.lang.Long.max


interface RevocationService {

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
     * Deletes all issued credentials that are not valid on the [cutoff] date any more.
     */
    fun deleteExpiredCredentialsBefore(cutoff: Instant): Int

}

/**
 * Gets emitted by [DefaultRevocationService] when a credential (issued in [timePeriod]) got revoked,
 * gets caught by [RevocationListScheduler] to update the cache of revocation lists.
 */
class RevocationEvent(source: Any, val timePeriod: Int) : ApplicationEvent(source) {
    override fun toString(): String {
        return "RevocationEvent(timePeriod=$timePeriod)"
    }
}

class DefaultRevocationService(
    private val credentialRepo: IssuedCredentialRepository,
    private val revokedCredentialRepo: RevokedCredentialRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
) : RevocationService {

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
    ): Long? =
        runCatching {
            synchronized(CredentialRepositoriesLock) {
                if (credentialRepo.findBytimePeriodAndVcId(timePeriod, vcId) != null)
                    return@runCatching null.also {
                        Napier.e("Tried to store a new credential for existing vcId")
                        Napier.v("vcId: '$vcId'")
                    }
                val revocationListIndex = max(
                    (credentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0),
                    revokedCredentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0
                ) + 1
                val attributeName = credentialSubject.javaClass.simpleName
                val issuedCredential = IssuedCredential(
                    vcId,
                    credentialSubject.id,
                    expirationDate.toJavaInstant(),
                    timePeriod,
                    attributeName,
                    revocationListIndex
                )
                val savedCredential = credentialRepo.save(issuedCredential)
                return@runCatching savedCredential.revocationListIndex
            }
        }.getOrElse { null.also { _ -> Napier.e("Database error", it) } }


    /**
     * Revokes one credential, specified by its [vcId] (which is a unique identifier
     * for one verifiable credential, in the form of `urn:uuid:${uuid4()}`)
     */
    override fun revokeCredentialsByVcId(vcId: String, timePeriod: Int): Int {
        val credential =
            credentialRepo.findBytimePeriodAndVcId(timePeriod, vcId) ?: return 0
        return revokeAllCredentials(listOf(credential))
    }

    private fun revokeAllCredentials(toRevoke: Collection<IssuedCredential>): Int {
        synchronized(CredentialRepositoriesLock) {
            revokedCredentialRepo.saveAll(toRevoke.map { RevokedCredential(it.timePeriod, it.revocationListIndex) })
            credentialRepo.deleteAllInBatch(toRevoke)
            toRevoke.map { it.timePeriod }.toSet()
                .forEach { applicationEventPublisher.publishEvent(RevocationEvent(this, it)) }
            return toRevoke.count()
        }
    }

    /**
     * Lists the field [IssuedCredential.revocationListIndex] for all credentials that have been revoked.
     */
    override fun getRevokedStatusListIndexList(timePeriod: Int): Collection<Long> {
        return revokedCredentialRepo.getRevocationListIndexByTimePeriod(timePeriod)
    }

    /**
     * Lists all non-revoked credentials that have been issued
     */
    override fun getAllNonRevokedWithDetails(): Collection<IssuedCredential> {
        return credentialRepo.findAllByValidUntilAfter(java.time.Instant.now())
    }

    /**
     * Deletes all issued credentials that are not valid on the [cutoff] date any more.
     */
    override fun deleteExpiredCredentialsBefore(cutoff: Instant): Int {
        // TODO Use synchronized(CredentialRepositoriesLock) here?
        val list = credentialRepo.findAllByValidUntilBefore(cutoff.toJavaInstant())
        list.forEach {
            Napier.i("Deleting credential")
            Napier.v("vcId: ${it.vcId}, subjectId: ${it.subjectId}")
            credentialRepo.delete(it)
        }
        return list.size
    }
}
