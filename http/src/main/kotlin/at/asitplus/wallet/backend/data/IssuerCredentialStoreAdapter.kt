package at.asitplus.wallet.backend.data

import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListView
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import kotlinx.datetime.Instant

/**
 * Implements interface [IssuerCredentialStore] from VC Library to wrap calls to [RevocationService].
 */
class IssuerCredentialStoreAdapter(
    private val revocationService: RevocationService,
) : IssuerCredentialStore {

    override fun setStatus(
        vcId: String,
        status: TokenStatus,
        timePeriod: Int,
    ): Boolean = revocationService.setStatus(vcId, status, timePeriod)

    override fun getStatusListView(timePeriod: Int): StatusListView = revocationService.getStatusListView(timePeriod)

    override suspend fun storeGetNextIndex(
        credential: IssuerCredentialStore.Credential,
        subjectPublicKey: at.asitplus.signum.indispensable.CryptoPublicKey,
        issuanceDate: Instant,
        expirationDate: Instant,
        timePeriod: Int,
    ): Long? = revocationService.storeGetNextIndex(
        issuanceDate,
        expirationDate,
        timePeriod,
        credential,
        subjectPublicKey,
    )

}