package at.asitplus.wallet.backend.service

import at.asitplus.wallet.backend.data.*
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListView
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusBitSize
import io.github.aakira.napier.Napier
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import java.lang.Long.max


interface RevocationService {

    fun setStatus(
        vcId: String,
        status: TokenStatus,
        timePeriod: Int,
    ): Boolean

    fun getStatusListView(timePeriod: Int): StatusListView

    /**
     * Stores the verifiable credential that is about to be issued,
     * and returns the [IssuedCredential.revocationListIndex] for this credential,
     * that will be included in the verifiable credentials (so that consumers
     * can verify the revocation status).
     */
    fun storeGetNextIndex(
        issuanceDate: Instant,
        expirationDate: Instant,
        timePeriod: Int,
        credential: IssuerCredentialStore.Credential,
        subjectPublicKey: at.asitplus.signum.indispensable.CryptoPublicKey,
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
        issuanceDate: Instant,
        expirationDate: Instant,
        timePeriod: Int,
        credential: IssuerCredentialStore.Credential,
        subjectPublicKey: at.asitplus.signum.indispensable.CryptoPublicKey,
    ): Long? =
        runCatching {
            synchronized(CredentialRepositoriesLock) {
                val id = credential.extractVcId()
                // we might store something later on ... index will not be used by vclib
                    ?: return 0
                if (credentialRepo.findBytimePeriodAndVcId(timePeriod, id) != null)
                    return@runCatching null.also {
                        Napier.e("Tried to store a new credential for existing vcId")
                        Napier.v("vcId: '$id'")
                    }
                val revocationListIndex = max(
                    (credentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0),
                    revokedCredentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0
                ) + 1
                val issuedCredential = IssuedCredential(
                    vcId = id,
                    subjectId = subjectPublicKey.didEncoded,
                    validUntil = expirationDate.toJavaInstant(),
                    timePeriod = timePeriod,
                    attributeName = credential.attributeName(),
                    revocationListIndex = revocationListIndex
                )
                val savedCredential = credentialRepo.save(issuedCredential)
                return@runCatching savedCredential.revocationListIndex
            }
        }.getOrElse { null.also { _ -> Napier.e("Database error", it) } }

    private fun IssuerCredentialStore.Credential.extractVcId() = when (this) {
        is IssuerCredentialStore.Credential.Iso -> null
        is IssuerCredentialStore.Credential.VcJwt -> vcId
        is IssuerCredentialStore.Credential.VcSd -> vcId
    }

    override fun setStatus(
        vcId: String,
        status: TokenStatus,
        timePeriod: Int,
    ): Boolean {
        val credential = credentialRepo.findBytimePeriodAndVcId(timePeriod, vcId)
            ?: return false
        return revokeAllCredentials(listOf(credential), status) == 1
    }

    private fun revokeAllCredentials(toRevoke: Collection<IssuedCredential>, status: TokenStatus): Int {
        synchronized(CredentialRepositoriesLock) {
            revokedCredentialRepo.saveAll(toRevoke.map {
                RevokedCredential(
                    it.revocationListIndex,
                    it.timePeriod,
                    status.value
                )
            })
            credentialRepo.deleteAllInBatch(toRevoke)
            toRevoke.map { it.timePeriod }.toSet()
                .forEach { applicationEventPublisher.publishEvent(RevocationEvent(this, it)) }
            return toRevoke.count()
        }
    }

    override fun getStatusListView(timePeriod: Int): StatusListView =
        StatusListView.fromTokenStatuses(
            revokedCredentialRepo.getByTimePeriod(timePeriod).map { TokenStatus(it.status) },
            TokenStatusBitSize.ONE
        )

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
            Napier.v("vcId: ${it.vcId}")
            credentialRepo.delete(it)
        }
        return list.size
    }
}

private fun IssuerCredentialStore.Credential.attributeName(): String = when (this) {
    is IssuerCredentialStore.Credential.Iso -> this.scheme.schemaUri
    is IssuerCredentialStore.Credential.VcJwt -> this.scheme.schemaUri
    is IssuerCredentialStore.Credential.VcSd -> this.scheme.schemaUri
}
