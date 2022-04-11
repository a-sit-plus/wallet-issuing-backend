package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.AtomicAttributeCredential
import at.asitplus.wallet.lib.data.CredentialSubject
import at.asitplus.wallet.lib.data.SchemaIndex
import org.slf4j.LoggerFactory
import kotlin.time.Duration


/**
 * Gets credentials for the currently authenticated user from
 * the previously stored attributes (from an OIDC login),
 * i.e. it looks up data with the `bpk` from its internal map
 */
class EidasCredentialDataProvider constructor(
    private val lifetime: Duration,
    private val deviceBindingStorageService: DeviceBindingStorageService,
) : IssuerCredentialDataProvider {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val map = mutableMapOf<String, EidasClaim>()

    fun storeClaims(bpk: String, eidasClaim: EidasClaim) {
        map[bpk] = eidasClaim
    }

    override fun getClaim(subjectId: String, attributeName: String): CredentialSubject? {
        if (!attributeName.startsWith(SchemaIndex.ATTR_GENERIC_PREFIX))
            return null

        val deviceBinding = deviceBindingStorageService.getDeviceBindingForCurrentUser()
            ?: return null.also {
                log.error("Got no authenticated user when trying to issue credentials")
            }

        val eidasClaim = map.remove(deviceBinding.bpk)
            ?: return null.also {
                log.error("Found no stored EIDAS claim for bpk '{}'", deviceBinding.bpk)
            }

        return when (attributeName.removePrefix(SchemaIndex.ATTR_GENERIC_PREFIX + "/")) {
            "given-name" -> AtomicAttributeCredential(subjectId, attributeName, eidasClaim.givenName)
            "family-name" -> AtomicAttributeCredential(subjectId, attributeName, eidasClaim.familyName)
            "date-of-birth" -> AtomicAttributeCredential(subjectId, attributeName, eidasClaim.birthdate)
            "identifier" -> AtomicAttributeCredential(subjectId, attributeName, eidasClaim.subject)
            else -> null
        }
    }

    override fun getCredential(subjectId: String, attributeType: String): CredentialSubject? {
        return null // not supported for EIDAS
    }

    override fun getLifetime(): Duration {
        return lifetime
    }

    data class EidasClaim(val subject: String, val birthdate: String, val givenName: String, val familyName: String)

}