package at.asitplus.wallet.backend.service

import at.asitplus.KmmResult
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.RevokedCredential
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.agent.IssuerCredentialStore.StoredCredentialReference
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.RevocationListInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListView
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.agents.ReferencedTokenStore
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.iso18013.Identifier
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.iso18013.IdentifierInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import org.springframework.context.ApplicationEvent
import kotlin.time.Instant


interface RevocationService {
    fun setStatus(
        timePeriod: Int,
        index: ULong,
        status: TokenStatus,
    ): Boolean

    fun getStatusListView(timePeriod: Int): StatusListView

    /**
     * Called by an [Issuer] when creating a new credential to get a `statusListIndex` first.
     * [Issuer] will call [onCredentialIssued] with the issued credential afterward.
     */
    suspend fun storeReferencedToken(
        credential: CredentialToBeIssued,
        timePeriod: Int,
    ): KmmResult<ReferencedTokenStore.StoredCredentialReference>

    @Suppress("DEPRECATION")
    suspend fun updateStoredCredential(
        reference: StoredCredentialReference,
        credential: Issuer.IssuedCredential,
    ): KmmResult<StoredCredentialReference>

    suspend fun onCredentialIssued(
        credential: Issuer.IssuedCredential,
    )

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

    fun revokeIdentifier(timePeriod: Int, identifier: ByteArray): Boolean

    /**
     * Lists the field [IssuedCredential.revocationListIndex] for all credentials that have been revoked.
     */
    fun getRevokedStatusListIndexList(timePeriod: Int): Collection<Long>

    /**
     * Deletes all issued credentials that are not valid on the [cutoff] date any more.
     */
    fun deleteExpiredCredentialsBefore(cutoff: Instant): Int

    fun getRawIdentifierList(timePeriod: Int): Map<Identifier, IdentifierInfo>

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
