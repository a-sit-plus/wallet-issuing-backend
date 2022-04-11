package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.data.CredentialSubject

interface CredentialDataProvider {

    fun getClaim(subjectId: String, attributeName: String, bpk: String): CredentialSubject?

    fun getCredential(subjectId: String, attributeType: String, bpk: String): CredentialSubject?

}