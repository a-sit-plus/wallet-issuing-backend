package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.AuthenticatedDeviceBindingUser
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.AtomicAttributeCredential
import at.asitplus.wallet.lib.data.CredentialSubject
import at.asitplus.wallet.lib.data.SchemaIndex
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.time.Duration


class EidasCredentialDataProvider constructor(
    private val lifetime: Duration,
) : IssuerCredentialDataProvider {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun getClaim(subjectId: String, attributeName: String): CredentialSubject? {
        if (!attributeName.startsWith(SchemaIndex.ATTR_GENERIC_PREFIX))
            return null
        val principal = SecurityContextHolder.getContext()?.authentication?.principal
        if (principal !is AuthenticatedDeviceBindingUser)
            return null.also {
                log.error("Got no authenticated user when trying to issue credentials")
            }
        // TODO get eidas attributes for principal.bpk
        return when (attributeName.removePrefix(SchemaIndex.ATTR_GENERIC_PREFIX + "/")) {
            "vorname" -> AtomicAttributeCredential(subjectId, attributeName, "eidas firstname")
            "nachname" -> AtomicAttributeCredential(subjectId, attributeName, "eidas lastname")
            else -> null
        }
    }

    override fun getCredential(subjectId: String, attributeType: String): CredentialSubject? {
        return null // not supported for EIDAS
    }

    override fun getLifetime(): Duration {
        return lifetime
    }

}