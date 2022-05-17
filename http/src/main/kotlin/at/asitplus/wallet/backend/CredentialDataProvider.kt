package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.data.CredentialSubject
import java.time.Duration

interface CredentialDataProvider {

    fun getClaim(subjectId: String, attributeName: String, bpk: String, maxLifetime: Duration)
            : CredentialToBeIssued?

    fun getCredential(subjectId: String, attributeType: String, bpk: String, maxLifetime: Duration)
            : CredentialToBeIssued?

    data class CredentialToBeIssued(
        val subject: CredentialSubject,
        val lifetime: Duration,
        val attributeType: String,
    )

}