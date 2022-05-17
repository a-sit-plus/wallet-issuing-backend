package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.data.CredentialSubject
import java.time.Duration
import java.time.Instant

interface CredentialDataProvider {

    fun getClaim(subjectId: String, attributeName: String, bpk: String, maxLifetime: Duration)
            : CredentialToBeIssued?

    fun getCredential(subjectId: String, attributeType: String, bpk: String, maxLifetime: Duration)
            : CredentialToBeIssued?

    data class CredentialToBeIssued(
        val subject: CredentialSubject,
        val expiration: Instant,
        val attributeType: String,
    )

}