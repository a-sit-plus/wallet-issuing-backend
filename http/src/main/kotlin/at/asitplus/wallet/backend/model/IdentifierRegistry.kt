package at.asitplus.wallet.backend.model

import at.asitplus.wallet.lib.agent.IssuerCredentialStore


class IdentifierRegistry(private val identifierRepository: IdentifierRepository) : IssuerCredentialStore {

    fun isRevoked(vcId: String): Boolean? {
        return identifierRepository.findByVcId(vcId)?.revoked
    }

    override fun revoke(vcId: String): Boolean {
        val identifier = identifierRepository.findByVcId(vcId) ?: return false
        identifier.revoked = true
        identifierRepository.save(identifier)
        return true
    }

    override fun storeGetNextIndex(vcId: String): Int? {
        if (identifierRepository.findByVcId(vcId) != null)
            return null
        val newIdentifier = identifierRepository.save(Identifier(vcId, false))
        return newIdentifier.revocationListIndex.toInt()
    }

    override fun getRevokedStatusListIndexList(): Collection<Int> {
        return identifierRepository.findAllByRevokedTrueOrderByRevocationListIndex().map {
            it.revocationListIndex.toInt()
        }
    }

    fun getAllNonRevoked(): List<String> {
        return identifierRepository.findAllByRevokedFalse().map { it.vcId }
    }

}