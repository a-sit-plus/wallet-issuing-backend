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
        identifierRepository.findByKey(vcId)?.let {
            it.revoked = true
            identifierRepository.save(it)
        } ?: throw Exception("Not registered")
    }

    @Throws(Exception::class)
    override fun storeGetNextIndex(vcId: String): Int {
        if (identifierRepository.findByKey(vcId) != null)
            throw Exception("Already registered")
        val newIdentifier = identifierRepository.save(Identifier(vcId, false))
        return newIdentifier.revocationListIndex.toInt()
    }

    override fun getRevocationList(): BooleanArray {
        val result = BooleanArray(identifierRepository.count().toInt()) { false }
        identifierRepository.findAllByRevokedTrueOrderByRevocationListIndex().forEach {
            result[it.revocationListIndex.toInt()] = true
        }
        return result
    }

}