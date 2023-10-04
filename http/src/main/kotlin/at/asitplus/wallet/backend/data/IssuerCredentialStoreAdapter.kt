package at.asitplus.wallet.backend.data

import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.data.CredentialSubject
import at.asitplus.wallet.lib.iso.IssuerSignedItem
import io.github.aakira.napier.Napier
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

    override fun storeGetNextIndex(
        vcId: String,
        credentialSubject: CredentialSubject,
        issuanceDate: kotlinx.datetime.Instant,
        expirationDate: kotlinx.datetime.Instant,
        timePeriod: Int
    ): Long? {
        return revocationService.storeGetNextIndex(
            vcId,
            credentialSubject,
            issuanceDate,
            expirationDate,
            timePeriod
        )
    }

    override fun storeGetNextIndex(
        issuerSignedItemList: List<IssuerSignedItem>,
        issuanceDate: Instant,
        expirationDate: Instant,
        timePeriod: Int
    ): Long? {
        Napier.e { "Revoking mDL data is unsupported ATM. This will be a real error in the future" }
        TODO("Return some temp value and figure it out")
    }

    override fun getRevokedStatusListIndexList(timePeriod: Int): Collection<Long> {
        return revocationService.getRevokedStatusListIndexList(timePeriod)
    }

}