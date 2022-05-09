package at.asitplus.wallet.backend.data

import at.asitplus.wallet.backend.RevocationService
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.data.CredentialSubject
import kotlinx.datetime.toJavaInstant

/**
 * Implements interface [IssuerCredentialStore] from VC Library to wrap calls to [RevocationService].
 */
class IssuerCredentialStoreAdapter(
    private val revocationService: RevocationService,
) : IssuerCredentialStore {

    override fun revoke(vcId: String): Boolean {
        return revocationService.revokeCredentialsByVcId(vcId) > 0
    }

    override fun storeGetNextIndex(
        vcId: String,
        credentialSubject: CredentialSubject,
        issuanceDate: kotlinx.datetime.Instant,
        expirationDate: kotlinx.datetime.Instant,
    ): Int? {
        return revocationService.storeGetNextIndex(
            vcId,
            credentialSubject,
            issuanceDate.toJavaInstant(),
            expirationDate.toJavaInstant()
        )
    }

    override fun getRevokedStatusListIndexList(): Collection<Int> {
        return revocationService.getRevokedStatusListIndexList()
    }

}