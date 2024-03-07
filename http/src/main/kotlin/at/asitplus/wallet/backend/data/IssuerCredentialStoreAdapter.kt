package at.asitplus.wallet.backend.data

import at.asitplus.crypto.datatypes.CryptoPublicKey
import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import kotlinx.datetime.Instant

/**
 * Implements interface [IssuerCredentialStore] from VC Library to wrap calls to [RevocationService].
 */
class IssuerCredentialStoreAdapter(
    private val revocationService: RevocationService,
) : IssuerCredentialStore {

    override fun revoke(vcId: String, timePeriod: Int): Boolean {
        return revocationService.revokeCredentialsByVcId(vcId, timePeriod) > 0
    }

    override fun getRevokedStatusListIndexList(timePeriod: Int): Collection<Long> {
        return revocationService.getRevokedStatusListIndexList(timePeriod)
    }

    override fun storeGetNextIndex(
        credential: IssuerCredentialStore.Credential,
        subjectPublicKey: CryptoPublicKey,
        issuanceDate: Instant,
        expirationDate: Instant,
        timePeriod: Int
    ): Long? {
        return revocationService.storeGetNextIndex(
            issuanceDate,
            expirationDate,
            timePeriod,
            credential,
            subjectPublicKey,
        )
    }

}