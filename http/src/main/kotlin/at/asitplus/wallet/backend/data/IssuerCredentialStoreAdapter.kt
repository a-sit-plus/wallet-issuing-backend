package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListView
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus

/**
 * Implements interface [IssuerCredentialStore] from VC Library to wrap calls to [RevocationService].
 */
class IssuerCredentialStoreAdapter(
    private val revocationService: RevocationService,
) : IssuerCredentialStore {

    override fun setStatus(
        timePeriod: Int,
        index: ULong,
        status: TokenStatus,
    ): Boolean = revocationService.setStatus(timePeriod, index, status)

    override fun getStatusListView(timePeriod: Int): StatusListView = revocationService.getStatusListView(timePeriod)

    /**
     * Called by an [Issuer] when creating a new credential to get a `statusListIndex` first.
     * [Issuer] will call [updateStoredCredential] with the issued credential afterwards.
     */
    override suspend fun createStatusListIndex(
        credential: CredentialToBeIssued,
        timePeriod: Int,
    ): KmmResult<IssuerCredentialStore.StoredCredentialReference> = revocationService.createStatusListIndex(
        credential = credential,
        timePeriod = timePeriod
    )

    /**
     * Called by an [Issuer] when the credential has been signed and delivered to the holder.
     */
    override suspend fun updateStoredCredential(
        reference: IssuerCredentialStore.StoredCredentialReference,
        credential: Issuer.IssuedCredential,
    ): KmmResult<IssuerCredentialStore.StoredCredentialReference> = revocationService.updateStoredCredential(
        reference = reference,
        credential = credential
    )
}