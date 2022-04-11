package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.data.AtomicAttributeCredential
import at.asitplus.wallet.lib.data.CredentialSubject
import at.asitplus.wallet.lib.data.SchemaIndex
import org.slf4j.LoggerFactory


/**
 * Gets credentials for the currently authenticated user from
 * the previously stored attributes (from an OIDC login),
 * i.e. it looks up data with the `bpk` from its internal map
 */
class EidasCredentialDataProvider : CredentialDataProvider {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val map = mutableMapOf<String, EidasClaim>()

    fun storeClaims(eidasClaim: EidasClaim, bpk: String) {
        map[bpk] = eidasClaim
    }

    override fun getClaim(subjectId: String, attributeName: String, bpk: String): CredentialSubject? {
        if (!attributeName.startsWith(SchemaIndex.ATTR_GENERIC_PREFIX))
            return null

        val eidasClaim = map.remove(bpk)
            ?: return null.also {
                log.error("Found no stored EIDAS claim for bpk '{}'", bpk)
            }

        return when (attributeName.removePrefix(SchemaIndex.ATTR_GENERIC_PREFIX + "/")) {
            "given-name" -> AtomicAttributeCredential(subjectId, attributeName, eidasClaim.givenName)
            "family-name" -> AtomicAttributeCredential(subjectId, attributeName, eidasClaim.familyName)
            "date-of-birth" -> AtomicAttributeCredential(subjectId, attributeName, eidasClaim.birthdate)
            "identifier" -> AtomicAttributeCredential(subjectId, attributeName, eidasClaim.subject)
            else -> null
        }
    }

    override fun getCredential(subjectId: String, attributeType: String, bpk: String): CredentialSubject? {
        return null // not supported for EIDAS
    }

    data class EidasClaim(val subject: String, val birthdate: String, val givenName: String, val familyName: String)

}