package at.asitplus.wallet.backend.model

import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import java.time.format.DateTimeFormatter


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

    override fun storeGetNextIndex(vcId: String, attributeName: String, subjectId: String): Int? {
        if (identifierRepository.findByVcId(vcId) != null)
            return null
        val newIdentifier = identifierRepository.save(Identifier(vcId, false, attributeName, subjectId))
        return newIdentifier.revocationListIndex.toInt()
    }

    override fun getRevokedStatusListIndexList(): Collection<Int> {
        return identifierRepository.findAllByRevokedTrueOrderByRevocationListIndex().map {
            it.revocationListIndex.toInt()
        }
    }

    fun getAllNonRevokedWithDetails(): List<RevocationListInfo> {
        return identifierRepository.findAllByRevokedFalse().map {
            RevocationListInfo(
                it.vcId,
                it.createdOn?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
                    ?: RevocationListInfo.DATE_ERROR_MSG, it.attributeName, it.subjectId
            )
        }
    }

    data class RevocationListInfo(
        val vcId: String,
        val issuanceDate: String,
        val attributeName: String,
        val subjectId: String
    ) {
        companion object {
            val DATE_ERROR_MSG = "Error saving the issuance date"
        }
    }

}