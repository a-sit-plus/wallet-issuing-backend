package at.asitplus.wallet.backend.model

import at.asitplus.wallet.lib.agent.IssuerCredentialStore


class IdentifierRegistry(private val identifierRepository: IdentifierRepository) : IssuerCredentialStore {

    @Throws(Exception::class)
    fun isRevoked(key: String): Boolean {
        return identifierRepository.findByKey(key)?.revoked
            ?: throw Exception("Not registered")
    }

    @Throws(Exception::class)
    override fun revoke(vcId: String) {
        val identifier = identifierRepository.findByKey(vcId) ?: throw Exception("Not registered")

        identifier.revoked = true
        identifierRepository.save(identifier)
    }

    @Throws(Exception::class)
    override fun storeGetNextIndex(vcId: String): Int {
        if (identifierRepository.findByKey(vcId) != null)
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
        return identifierRepository.findAllByRevokedFalse().map { it.key }
    }

}