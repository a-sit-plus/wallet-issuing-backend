package at.asitplus.wallet.backend.model

import at.asitplus.wallet.lib.agent.IssuerCredentialStore


class IdentifierRegistry(private val identifierRepository: IdentifierRepository) : IssuerCredentialStore {

    @Throws(Exception::class)
    fun isRevoked(vcId: String): Boolean {
        return identifierRepository.findByVcId(vcId)?.revoked
            ?: throw Exception("Not registered")
    }

    @Throws(Exception::class)
    override fun revoke(vcId: String) {
        val identifier = identifierRepository.findByVcId(vcId) ?: throw Exception("Not registered")

        identifier.revoked = true
        identifierRepository.save(identifier)
    }

    @Throws(Exception::class)
    override fun storeGetNextIndex(vcId: String): Int {
        if (identifierRepository.findByVcId(vcId) != null)
            throw Exception("Already registered")
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