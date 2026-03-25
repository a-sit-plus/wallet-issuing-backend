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
import at.asitplus.wallet.backend.data.PreparedCredential
import at.asitplus.wallet.backend.data.PreparedCredentialRepository
import at.asitplus.wallet.backend.data.RevokedCredential
import at.asitplus.wallet.backend.data.RevokedCredentialRepository
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.FixedTimePeriodProvider
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListView
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
    private val preparedCredentialRepo: PreparedCredentialRepository,
    private val issuedCredentialRepo: IssuedCredentialRepository,
    private val revokedCredentialRepo: RevokedCredentialRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val identityColumnResynchronizer: IdentityColumnResynchronizer,
) : RevocationService {

    /**
     * Checks whether a credential with [vcId] is revoked i.e. whether it exists or not
     *
     */
    override fun isRevoked(vcId: String, timePeriod: Int): Boolean {
        return issuedCredentialRepo.findBytimePeriodAndVcId(timePeriod, vcId) == null
    }

    /**
     * Called by an [at.asitplus.wallet.lib.agent.Issuer] when creating a new credential to get a `statusListIndex` first.
     * [at.asitplus.wallet.lib.agent.Issuer] will call [updateStoredCredential] with the issued credential afterwards.
     */
    override suspend fun createStoredCredentialReference(
        credential: CredentialToBeIssued,
        timePeriod: Int,
    ): KmmResult<IssuerCredentialStore.StoredCredentialReference> = catching {
        synchronized(CredentialRepositoriesLock) {
            Napier.v("Storing new credential for $credential")
            val revocationListIndex: Long = NumberUtils.max(
                preparedCredentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0,
                issuedCredentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0,
                revokedCredentialRepo.getMaxRevocationListIndex(timePeriod) ?: 0
            ) + 1
            val savedCredential = savePreparedCredential(
                PreparedCredential(
                    timePeriod = timePeriod,
                    revocationListIndex = revocationListIndex
                )
            )
            IssuerCredentialStore.StoredCredentialReference(
                savedCredential.id.toString(),
                timePeriod,
                revocationListIndex.toULong()
            )
        }
    }

    /**
     * Called by an [at.asitplus.wallet.lib.agent.Issuer] when the credential has been signed and delivered to the holder.
     */
    override suspend fun updateStoredCredential(
        reference: IssuerCredentialStore.StoredCredentialReference,
        credential: Issuer.IssuedCredential,
    ): KmmResult<IssuerCredentialStore.StoredCredentialReference> = catching {
        synchronized(CredentialRepositoriesLock) {
            Napier.v("Storing new credential for $credential")
            val savedCredential = preparedCredentialRepo.findById(reference.id.toLong()).getOrNull()
                ?: throw IllegalStateException("No credential found for id ${reference.id}")

            val issuedCredential = saveIssuedCredential(
                IssuedCredential(
                    vcId = credential.vcId,
                    subjectId = credential.subjectPublicKey.subjectId(),
                    userInfoSubject = credential.userInfo.matchedSubject(),
                    validUntil = credential.validUntil.toJavaInstant(),
                    timePeriod = savedCredential.timePeriod,
                    attributeName = credential.credentialName,
                    revocationListIndex = savedCredential.revocationListIndex,
                )
            )
            preparedCredentialRepo.delete(savedCredential)
            IssuerCredentialStore.StoredCredentialReference(
                issuedCredential.id.toString(),
                FixedTimePeriodProvider.timePeriod,
                issuedCredential.revocationListIndex.toULong()
            )
        }
    }

    private fun savePreparedCredential(preparedCredential: PreparedCredential): PreparedCredential =
        saveWithIdentityRecovery(
            tableName = "prepared_credential",
            resetEntityId = { preparedCredential.id = 0 },
        ) {
            preparedCredentialRepo.save(preparedCredential)
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
        Napier.w("Detected a drifted identity column for $tableName, resynchronizing and retrying the insert", exception)
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
