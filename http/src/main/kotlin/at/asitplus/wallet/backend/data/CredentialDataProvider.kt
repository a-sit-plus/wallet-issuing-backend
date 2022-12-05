package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.wallet.lib.data.CredentialSubject
import kotlinx.datetime.Instant

interface CredentialDataProvider {

    fun getClaim(subjectId: String, attributeName: String, bpk: String, maxExpiration: Instant)
            : KmmResult<CredentialToBeIssued>

    fun getCredential(subjectId: String, attributeType: String, bpk: String, maxExpiration: Instant)
            : KmmResult<CredentialToBeIssued>

    data class CredentialToBeIssued(
        val subject: CredentialSubject,
        val expiration: Instant,
        val attributeType: String,
    )
}