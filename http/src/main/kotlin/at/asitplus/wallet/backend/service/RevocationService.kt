package at.asitplus.wallet.backend.service

import at.asitplus.KmmResult
import at.asitplus.catching
import at.asitplus.iso.sha256
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.asn1.encodeToPEM
import at.asitplus.signum.indispensable.cosef.io.Base16Strict
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.wallet.backend.data.CredentialRepositoriesLock
import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.data.PreparedCredential
import at.asitplus.wallet.backend.data.PreparedCredentialRepository
import at.asitplus.wallet.backend.data.RevokedCredential
import at.asitplus.wallet.backend.data.RevokedCredentialRepository
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.FixedTimePeriodProvider.timePeriod
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.agent.IssuerCredentialStore.StoredCredentialReference
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListView
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusBitSize
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import org.apache.commons.lang3.math.NumberUtils.max
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import java.lang.Long.max
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Instant
import kotlin.time.toJavaInstant


interface RevocationService {
    fun setStatus(
        timePeriod: Int,
        index: ULong,
        status: TokenStatus,
    ): Boolean

    fun setStatus(
        vcId: String,
        status: TokenStatus,
        timePeriod: Int,
    ): Boolean

    fun getStatusListView(timePeriod: Int): StatusListView

    /**
     * Called by an [Issuer] when creating a new credential to get a `statusListIndex` first.
     * [Issuer] will call [updateStoredCredential] with the issued credential afterwards.
     */
    suspend fun createStatusListIndex(
        credential: CredentialToBeIssued,
        timePeriod: Int,
    ): KmmResult<StoredCredentialReference>

    /**
     * Called by an [Issuer] when the credential has been signed and delivered to the holder.
     */
    suspend fun updateStoredCredential(
        reference: StoredCredentialReference,
        credential: Issuer.IssuedCredential,
    ): KmmResult<StoredCredentialReference>

    /**
     * Stores the verifiable credential that is about to be issued,
     * and returns the [IssuedCredential.revocationListIndex] for this credential,
     * that will be included in the verifiable credentials (so that consumers
     * can verify the revocation status).
     */
    @Deprecated("Use `createStatusListIndex` and `updateStoredCredential` instead")
    // TODO check usages
    fun storeGetNextIndex(
        userInfo: OidcUserInfoExtended?,
        issuanceDate: Instant,
        expirationDate: Instant,
        timePeriod: Int,
        @Suppress("DEPRECATION") credential: IssuerCredentialStore.Credential,
        subjectPublicKey: CryptoPublicKey,
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
     * Lists all non-revoked credentials for one user
     */
    fun getAllNonRevokedForUser(userInfo: OidcUserInfoExtended): Collection<IssuedCredential>

    /**
     * Lists all revoked credentials for one user
     */
    fun getAllRevokedForUser(userInfo: OidcUserInfoExtended): Collection<RevokedCredential>

    /**
     * Revokes one credential for one user
     */
    fun revoke(id: Long, userInfo: OidcUserInfoExtended): Boolean

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
    private val preparedCredentialRepo: PreparedCredentialRepository,
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
     * Called by an [Issuer] when creating a new credential to get a `statusListIndex` first.
     * [Issuer] will call [updateStoredCredential] with the issued credential afterwards.
     */
    override suspend fun createStatusListIndex(
        credential: CredentialToBeIssued,
        timePeriod: Int,
    ): KmmResult<StoredCredentialReference> = catching {
        synchronized(CredentialRepositoriesLock) {
            Napier.v("Storing new credential for $credential")
            val revocationListIndex: Long = max(
                preparedCredentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0,
                credentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0,
                revokedCredentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0
            ) + 1
            val savedCredential = preparedCredentialRepo.save(
                PreparedCredential(
                    timePeriod = timePeriod,
                    revocationListIndex = revocationListIndex
                )
            )
            StoredCredentialReference(savedCredential.id.toString(), timePeriod, revocationListIndex.toULong())
        }
    }

    /**
     * Called by an [Issuer] when the credential has been signed and delivered to the holder.
     */
    override suspend fun updateStoredCredential(
        reference: StoredCredentialReference,
        credential: Issuer.IssuedCredential,
    ): KmmResult<StoredCredentialReference> = catching {
        synchronized(CredentialRepositoriesLock) {
            Napier.v("Storing new credential for $credential")
            val savedCredential = preparedCredentialRepo.findById(reference.id.toLong()).getOrNull()
                ?: throw IllegalStateException("No credential found for id ${reference.id}")

            val issuedCredential = credentialRepo.save(
                IssuedCredential(
                    vcId = credential.vcId,
                    subjectId = credential.subjectPublicKey.encodeToPEM().getOrNull()
                        ?: credential.subjectPublicKey.didEncoded,
                    userInfoSubject = credential.userInfo.userInfo.subject,
                    validUntil = credential.validUntil.toJavaInstant(),
                    timePeriod = savedCredential.timePeriod,
                    attributeName = credential.credentialName,
                    revocationListIndex = savedCredential.revocationListIndex,
                )
            )
            preparedCredentialRepo.delete(savedCredential)
            applicationEventPublisher.publishEvent(RevocationEvent(this, timePeriod))
            StoredCredentialReference(
                issuedCredential.id.toString(),
                timePeriod,
                issuedCredential.revocationListIndex.toULong()
            )
        }
    }

    /**
     * Stores the verifiable credential that is about to be issued,
     * and returns the [IssuedCredential.revocationListIndex] for this credential,
     * that will be included in the verifiable credentials (so that consumers
     * can verify the revocation status).
     */
    override fun storeGetNextIndex(
        userInfo: OidcUserInfoExtended?,
        issuanceDate: Instant,
        expirationDate: Instant,
        timePeriod: Int,
        credential: IssuerCredentialStore.Credential,
        subjectPublicKey: CryptoPublicKey,
    ): Long? = runCatching {
        synchronized(CredentialRepositoriesLock) {
            if (credentialRepo.findBytimePeriodAndVcId(timePeriod, credential.vcId) != null)
                return@runCatching null.also {
                    Napier.e("Tried to store a new credential for existing vcId")
                    Napier.v("vcId: '${credential.vcId}'")
                }
            val userInfoSubject = userInfo?.userInfo?.subject ?: "unknown"
            Napier.v("Storing new credential for userInfoSubject '$userInfoSubject")
            val revocationListIndex = max(
                preparedCredentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0,
                credentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0,
                revokedCredentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0
            ) + 1
            val issuedCredential = IssuedCredential(
                vcId = credential.vcId,
                subjectId = subjectPublicKey.encodeToPEM().getOrNull() ?: subjectPublicKey.didEncoded,
                userInfoSubject = userInfoSubject,
                validUntil = expirationDate.toJavaInstant(),
                timePeriod = timePeriod,
                attributeName = credential.attributeName,
                revocationListIndex = revocationListIndex
            )
            val savedCredential = credentialRepo.save(issuedCredential)
            // trigger writing new revocation list, because we'll need the new index on the list!
            applicationEventPublisher.publishEvent(RevocationEvent(this, timePeriod))
            return@runCatching savedCredential.revocationListIndex
        }
    }.getOrElse { null.also { _ -> Napier.e("Database error", it) } }

    override fun setStatus(
        timePeriod: Int,
        index: ULong,
        status: TokenStatus,
    ): Boolean {
        val credential = credentialRepo.findBytimePeriodAndRevocationListIndex(timePeriod, index.toLong())
            ?: return false
        return revokeAllCredentials(listOf(credential), status) == 1
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
                    revocationListIndex = it.revocationListIndex,
                    timePeriod = it.timePeriod,
                    status = status.value,
                    userInfoSubject = it.userInfoSubject,
                )
            })
            credentialRepo.deleteAllInBatch(toRevoke)
            toRevoke.map { it.timePeriod }.toSet()
                .forEach { applicationEventPublisher.publishEvent(RevocationEvent(this, it)) }
            return toRevoke.count()
        }
    }

    override fun getStatusListView(timePeriod: Int): StatusListView =
        StatusListView.fromTokenStatuses(tokenStatusForAllIndexes(timePeriod), TokenStatusBitSize.ONE)

    private fun tokenStatusForAllIndexes(timePeriod: Int): List<TokenStatus> {
        val revoked = revokedCredentialRepo.getByTimePeriod(timePeriod)
        val maxRevocationListIndex = max(
            credentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0,
            revokedCredentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0
        )
        return List(maxRevocationListIndex.toInt()) { listIndex ->
            TokenStatus(revoked.findIndex(listIndex)?.status ?: TokenStatus.Valid.value)
        }
    }

    private fun Collection<RevokedCredential>.findIndex(listIndex: Int): RevokedCredential? =
        find { it.revocationListIndex == listIndex.toLong() }

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

    override fun getAllNonRevokedForUser(userInfo: OidcUserInfoExtended): Collection<IssuedCredential> =
        credentialRepo.findAllByUserInfoSubjectAndValidUntilAfter(userInfo.userInfo.subject, java.time.Instant.now())

    override fun getAllRevokedForUser(userInfo: OidcUserInfoExtended): Collection<RevokedCredential> =
        revokedCredentialRepo.findAllByUserInfoSubject(userInfo.userInfo.subject)

    override fun revoke(id: Long, userInfo: OidcUserInfoExtended): Boolean =
        credentialRepo.findByIdAndUserInfoSubject(id, userInfo.userInfo.subject).getOrNull()?.let {
            Napier.d("/revoke/$id for $it")
            setStatus(it.vcId, TokenStatus.Invalid, it.timePeriod)
        } ?: false

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

private val IssuerCredentialStore.Credential.attributeName: String
    get() = when (this) {
        is IssuerCredentialStore.Credential.Iso -> this.scheme.isoNamespace ?: this.scheme.schemaUri
        is IssuerCredentialStore.Credential.VcJwt -> this.scheme.vcType ?: this.scheme.schemaUri
        is IssuerCredentialStore.Credential.VcSd -> this.scheme.sdJwtType ?: this.scheme.schemaUri
    }

@OptIn(ExperimentalSerializationApi::class)
private val Issuer.IssuedCredential.vcId: String
    get() = when (this) {
        is Issuer.IssuedCredential.Iso -> coseCompliantSerializer.encodeToByteArray(this.issuerSigned).hashString()
        is Issuer.IssuedCredential.VcJwt -> this.vc.id
        is Issuer.IssuedCredential.VcSdJwt -> this.sdJwtVc.jwtId ?: this.signedSdJwtVc.serialize().hashString()
    }

private val Issuer.IssuedCredential.credentialName: String
    get() = when (this) {
        is Issuer.IssuedCredential.Iso -> this.scheme.isoNamespace ?: this.scheme.schemaUri
        is Issuer.IssuedCredential.VcJwt -> this.scheme.vcType ?: this.scheme.schemaUri
        is Issuer.IssuedCredential.VcSdJwt -> this.scheme.sdJwtType ?: this.scheme.schemaUri
    }

private val Issuer.IssuedCredential.validUntil: Instant
    get() = when (this) {
        is Issuer.IssuedCredential.Iso -> this.issuerSigned.issuerAuth.payload?.validityInfo?.validUntil
            ?: Instant.DISTANT_PAST

        is Issuer.IssuedCredential.VcJwt -> this.vc.expirationDate ?: Instant.DISTANT_PAST
        is Issuer.IssuedCredential.VcSdJwt -> this.sdJwtVc.expiration ?: Instant.DISTANT_PAST
    }

private fun String.hashString() = this.encodeToByteArray().hashString()
private fun ByteArray.hashString() = sha256().encodeToString(Base16Strict)