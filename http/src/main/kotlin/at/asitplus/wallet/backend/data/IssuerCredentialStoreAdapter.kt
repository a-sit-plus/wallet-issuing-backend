package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.wallet.backend.service.RevocationListWriter
import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListView
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.agents.ReferencedTokenStore
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.iso18013.Identifier
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.iso18013.IdentifierInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy

/**
 * Implements interface [IssuerCredentialStore] from VC Library to wrap calls to [RevocationService].
 */
class IssuerCredentialStoreAdapter(
    private val revocationService: RevocationService,
) : IssuerCredentialStore, ReferencedTokenStore {

    // Break the circular dependency
    @Autowired
    @Lazy
    lateinit var revocationListWriter: RevocationListWriter

    override fun setStatus(
        timePeriod: Int,
        index: ULong,
        status: TokenStatus,
    ): Boolean = revocationService.setStatus(timePeriod, index, status)

    override fun revokeIdentifier(timePeriod: Int, identifier: ByteArray): Boolean =
        revocationService.revokeIdentifier(timePeriod, identifier)

    override suspend fun storeReferencedToken(
        credential: CredentialToBeIssued,
        timePeriod: Int,
    ): KmmResult<ReferencedTokenStore.StoredCredentialReference> =
        revocationService.storeReferencedToken(credential, timePeriod).onSuccess {
            // need to do this immediately, so clients receiving our credential can check the status list right away
            revocationListWriter.writeRevocationList(it.timePeriod)
        }

    override fun getStatusListView(timePeriod: Int): StatusListView =
        revocationService.getStatusListView(timePeriod)

    override fun getRawIdentifierList(timePeriod: Int): Map<Identifier, IdentifierInfo> =
        revocationService.getRawIdentifierList(timePeriod)

    /**
     * Called by an [Issuer] when the credential has been signed and delivered to the holder.
     */
    override suspend fun onCredentialIssued(
        credential: Issuer.IssuedCredential,
    ) {
        // nothing to do, since we've already stored the issuedCredential in storeReferencedToken
    }
}
