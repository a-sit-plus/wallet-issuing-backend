package at.asitplus.wallet.backend.service

import at.asitplus.KmmResult
import at.asitplus.catching
import at.asitplus.iso.sha256
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.cosef.io.Base16Strict
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.data.CredentialRepositoriesLock
import at.asitplus.wallet.backend.data.IdentityColumnResynchronizer
import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.data.RevokedCredential
import at.asitplus.wallet.backend.data.RevokedCredentialRepository
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.RevocationListInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListView
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.agents.ReferencedTokenStore
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.iso18013.Identifier
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.iso18013.IdentifierInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusBitSize
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import org.apache.commons.lang3.math.NumberUtils
import org.springframework.context.ApplicationEventPublisher
import java.sql.SQLException
import kotlin.io.encoding.Base64
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class DefaultRevocationService(
    private val issuedCredentialRepo: IssuedCredentialRepository,
    private val revokedCredentialRepo: RevokedCredentialRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val identityColumnResynchronizer: IdentityColumnResynchronizer,
) : RevocationService {

    /**
     * Checks whether a credential with [vcId] is revoked i.e. whether it exists or not
     */
    override fun isRevoked(vcId: String, timePeriod: Int): Boolean {
        return issuedCredentialRepo.findBytimePeriodAndVcId(timePeriod, vcId) == null
    }

    /**
     * Called by an [at.asitplus.wallet.lib.agent.Issuer] when creating a new credential to get a `statusListIndex` first.
     * [at.asitplus.wallet.lib.agent.Issuer] will call [updateStoredCredential] with the issued credential afterwards.
     */
    override suspend fun storeReferencedToken(
        credential: CredentialToBeIssued,
        timePeriod: Int,
    ): KmmResult<ReferencedTokenStore.StoredCredentialReference> = catching {
        synchronized(CredentialRepositoriesLock) {
            Napier.v("Storing new credential for $credential")
            val revocationListIndex: Long = NumberUtils.max(
                issuedCredentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0,
                revokedCredentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0
            ) + 1
            val savedCredential = saveIssuedCredential(
                IssuedCredential(
                    vcId = "dontcare",
                    subjectId = credential.subjectPublicKey.subjectId(),
                    userInfoSubject = credential.userInfo.matchedSubject(),
                    validUntil = credential.expiration.toJavaInstant(),
                    timePeriod = timePeriod,
                    attributeName = credential.credentialName,
                    revocationListIndex = revocationListIndex
                )
            )
            ReferencedTokenStore.StoredCredentialReference(
                id = savedCredential.id.toString(),
                timePeriod = timePeriod,
                statusListIndex = revocationListIndex.toULong()
            )
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun updateStoredCredential(
        reference: IssuerCredentialStore.StoredCredentialReference,
        credential: Issuer.IssuedCredential,
    ): KmmResult<IssuerCredentialStore.StoredCredentialReference> = catching {
        TODO() // Should never be called from VC-K, safe to throw here
    }

    override suspend fun onCredentialIssued(
        credential: Issuer.IssuedCredential,
    ) {
        catching {
            synchronized(CredentialRepositoriesLock) {
                // TODO: Probably nothing to do, since we've already stored the issuedCredential in storeReferencedToken
            }
        }
    }

    private fun saveIssuedCredential(issuedCredential: IssuedCredential): IssuedCredential =
        saveWithIdentityRecovery(
            tableName = "issued_credential",
            resetEntityId = { issuedCredential.id = 0 },
        ) {
            issuedCredentialRepo.save(issuedCredential)
        }

    private fun <T> saveWithIdentityRecovery(
        tableName: String,
        resetEntityId: () -> Unit,
        save: () -> T,
    ): T = try {
        save()
    } catch (exception: RuntimeException) {
        if (!exception.isDuplicatePrimaryKeyOn(tableName)) {
            throw exception
        }
        Napier.w(
            "Detected a drifted identity column for $tableName, resynchronizing and retrying the insert",
            exception
        )
        identityColumnResynchronizer.resynchronize(tableName)
        resetEntityId()
        save()
    }

    private fun CryptoPublicKey.subjectId(): String =
        catching { Base64.Mime.encode(encodeToDer()).lines().joinToString("") }.getOrNull() ?: didEncoded

    override fun setStatus(
        timePeriod: Int,
        index: ULong,
        status: TokenStatus,
    ): Boolean {
        val credential = issuedCredentialRepo.findBytimePeriodAndRevocationListIndex(timePeriod, index.toLong())
            ?: return false
        return revokeAllCredentials(listOf(credential), status) == 1
    }

    override fun revokeIdentifier(timePeriod: Int, identifier: ByteArray): Boolean {
        val id = identifier.decodeToString().toLongOrNull()
            ?: return false
        val credential = issuedCredentialRepo.findById(id).getOrNull()
            ?: return false
        return revokeAllCredentials(listOf(credential), TokenStatus.Invalid) == 1
    }

    private fun revokeAllCredentials(toRevoke: Collection<IssuedCredential>, status: TokenStatus): Int {
        synchronized(CredentialRepositoriesLock) {
            revokedCredentialRepo.saveAll(toRevoke.map {
                RevokedCredential(
                    timePeriod = it.timePeriod,
                    revocationListIndex = it.revocationListIndex,
                    identifier = it.id,
                    status = status.value,
                    userInfoSubject = it.userInfoSubject,
                )
            })
            revokedCredentialRepo.flush()
            issuedCredentialRepo.deleteAll(toRevoke)
            issuedCredentialRepo.flush()
            toRevoke.map { it.timePeriod }.toSet()
                .forEach { applicationEventPublisher.publishEvent(RevocationEvent(this, it)) }
            return toRevoke.count()
        }
    }

    override fun getStatusListView(timePeriod: Int): StatusListView =
        StatusListView.fromTokenStatuses(tokenStatusForAllIndexes(timePeriod), TokenStatusBitSize.ONE)

    // TODO Long toString to ByteArray looks sketchy
    override fun getRawIdentifierList(timePeriod: Int): Map<Identifier, IdentifierInfo> =
        revokedCredentialRepo.getByTimePeriod(timePeriod)
            .filter { it.identifier != null }
            .associate { Identifier(it.identifier.toString().encodeToByteArray()) to IdentifierInfo() }

    private fun tokenStatusForAllIndexes(timePeriod: Int): List<TokenStatus> {
        val revoked = revokedCredentialRepo.getByTimePeriod(timePeriod)
        val maxRevocationListIndex = java.lang.Long.max(
            issuedCredentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0,
            revokedCredentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0
        )
        // Revocation list indexes are 1-based, so the list needs `maxRevocationListIndex + 1` entries
        // for the credential at the highest index (e.g. the one just issued) to be included and reported as valid.
        require(maxRevocationListIndex + 1 < Int.MAX_VALUE) { "Revocation list too large" }
        return List(maxRevocationListIndex.toInt() + 1) { listIndex ->
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
        return issuedCredentialRepo.findAllByValidUntilAfter(Clock.System.now().toJavaInstant())
    }

    override fun getAllNonRevokedForUser(userInfo: OidcUserInfoExtended): Collection<IssuedCredential> =
        issuedCredentialRepo.findAllByUserInfoSubjectAndValidUntilAfter(
            userInfo.matchedSubject(),
            Clock.System.now().toJavaInstant()
        )

    override fun getAllRevokedForUser(userInfo: OidcUserInfoExtended): Collection<RevokedCredential> =
        revokedCredentialRepo.findAllByUserInfoSubject(userInfo.matchedSubject())

    override fun revoke(id: Long, userInfo: OidcUserInfoExtended): Boolean =
        issuedCredentialRepo.findByIdAndUserInfoSubject(id, userInfo.matchedSubject()).getOrNull()?.let {
            Napier.d("${Paths.RevokeUrl}/$id for $it")
            setStatus(it.timePeriod, it.revocationListIndex.toULong(), TokenStatus.Invalid)
        } ?: false

    /**
     * `subject` received from ID Austria is not a stable identifier, so we'll need to come up with our own.
     * Collisions might happen, but this is just a development stage.
     */
    private fun OidcUserInfoExtended.matchedSubject(): String = with(userInfo) {
        if (givenName?.isNotEmpty() == true && familyName?.isNotEmpty() == true && birthDate?.isNotEmpty() == true)
            "$givenName $familyName $birthDate"
        else
            subject
    }

    /**
     * Deletes all issued credentials that are not valid on the [cutoff] date any more.
     */
    override fun deleteExpiredCredentialsBefore(cutoff: Instant): Int {
        // TODO Use synchronized(CredentialRepositoriesLock) here?
        val list = issuedCredentialRepo.findAllByValidUntilBefore(cutoff.toJavaInstant())
        list.forEach {
            Napier.i("Deleting credential")
            Napier.v("vcId: ${it.vcId}")
            issuedCredentialRepo.delete(it)
        }
        return list.size
    }
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
        is Issuer.IssuedCredential.Iso -> this.scheme.isoNamespace
        is Issuer.IssuedCredential.VcJwt -> this.scheme.vcType
        is Issuer.IssuedCredential.VcSdJwt -> this.scheme.sdJwtType
    }

private val CredentialToBeIssued.credentialName: String
    get() = when (this) {
        is CredentialToBeIssued.Iso -> this.scheme.isoNamespace
        is CredentialToBeIssued.VcJwt -> this.scheme.vcType
        is CredentialToBeIssued.VcSd -> this.scheme.sdJwtType
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

private fun Throwable.isDuplicatePrimaryKeyOn(tableName: String): Boolean =
    generateSequence(this) { it.cause }.any { cause ->
        if (cause !is SQLException || cause.sqlState != "23505") {
            return@any false
        }
        val message = cause.message?.lowercase() ?: return@any false
        val normalizedTableName = tableName.lowercase()
        message.contains(normalizedTableName) ||
                message.contains("${normalizedTableName}_pkey") ||
                message.contains("primary key")
    }
