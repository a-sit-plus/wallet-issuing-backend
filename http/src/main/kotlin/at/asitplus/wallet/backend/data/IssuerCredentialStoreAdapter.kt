package at.asitplus.wallet.backend.data

import at.asitplus.wallet.backend.RevocationService
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.data.CredentialSubject

/**
 * Implements interface [IssuerCredentialStore] from VC Library to wrap calls to [RevocationService].
 */
class IssuerCredentialStoreAdapter(
    private val revocationService: RevocationService,
) : IssuerCredentialStore {

    override fun revoke(vcId: String, timePeriod: Int): Boolean {
        return revocationService.revokeCredentialsByVcId(vcId, timePeriod) > 0
    }

    override fun storeGetNextIndex(
        vcId: String,
        credentialSubject: CredentialSubject,
        issuanceDate: kotlinx.datetime.Instant,
        expirationDate: kotlinx.datetime.Instant,
        timePeriod: Int
    ): Int? {
        return revocationService.storeGetNextIndex(
            vcId,
            credentialSubject,
            issuanceDate,
            expirationDate,
            timePeriod
        )
    }

    override fun getRevokedStatusListIndexList(timePeriod: Int): Collection<Int> {
        return revocationService.getRevokedStatusListIndexList(timePeriod)
    }

}