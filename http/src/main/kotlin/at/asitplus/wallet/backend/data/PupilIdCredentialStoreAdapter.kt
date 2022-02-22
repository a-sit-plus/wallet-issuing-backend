package at.asitplus.wallet.backend.data

import at.asitplus.wallet.backend.PupilIdRevocationService
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.data.CredentialSubject

/**
 * Implements interface from VC Library to wrap calls to [PupilIdRevocationService]
 */
class PupilIdCredentialStoreAdapter(
    private val pupilIdRevocationService: PupilIdRevocationService,
) : IssuerCredentialStore {

    override fun revoke(vcId: String): Boolean {
        return pupilIdRevocationService.revokeCredentialsByVcId(vcId)
    }

    override fun storeGetNextIndex(
        vcId: String,
        credentialSubject: CredentialSubject,
        issuanceDate: kotlinx.datetime.Instant,
        expirationDate: kotlinx.datetime.Instant,
    ): Int? {
        return pupilIdRevocationService.storeGetNextIndex(vcId, credentialSubject, issuanceDate, expirationDate)
    }

    override fun getRevokedStatusListIndexList(): Collection<Int> {
        return pupilIdRevocationService.getRevokedStatusListIndexList()
    }

}