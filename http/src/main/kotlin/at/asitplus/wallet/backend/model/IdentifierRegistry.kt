package at.asitplus.wallet.backend.model

import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.data.AtomicAttributeCredential
import at.asitplus.wallet.lib.data.CredentialSubject
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

    override fun storeGetNextIndex(vcId: String, credentialSubject: CredentialSubject): Int? {
        if (identifierRepository.findByVcId(vcId) != null)
            return null
        if (credentialSubject is AtomicAttributeCredential) {
            return identifierRepository.save(
                Identifier(vcId, false, credentialSubject.name, credentialSubject.id)
            ).revocationListIndex.toInt()
        } else {
            return identifierRepository.save(
                Identifier(vcId, false, "TODO", credentialSubject.id)
            ).revocationListIndex.toInt()
        }
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