package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.AuthenticatedDeviceBindingUser
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.CredentialSubject
import at.asitplus.wallet.lib.data.PupilIdCredential
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import kotlin.time.Duration


class EcoCredentialDataProvider constructor(
    private val lifetime: Duration,
    private val url: String,
    private val restTemplate: RestTemplate,
) : IssuerCredentialDataProvider {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun getClaim(subjectId: String, attributeName: String): CredentialSubject? {
        return null // not supported for ECO
    }

    override fun getCredential(subjectId: String, attributeType: String): CredentialSubject? {
        if (attributeType != ConstantIndex.PupilId.vcType)
            return null

        val principal = SecurityContextHolder.getContext()?.authentication?.principal
        val bpk = (principal as? AuthenticatedDeviceBindingUser)?.bpk
            ?: return null.also {
                log.error("Got no authenticated user when trying to issue credentials")
            }
        val entity = restTemplate.getForEntity<EcoStudentData>(
            "$url/Student/{bpk}",
            uriVariables = mapOf("bpk" to bpk)
        )
        log.debug("getCredential for '{}' got {}", bpk, entity)

        return entity.body?.let {
            PupilIdCredential(
                id = subjectId,
                schoolName = "unknown",
                schoolAddress = "unknown",
                schoolNumber = "unknown",
                pupilNumber = "unknown",
                firstName = it.firstname,
                lastName = it.lastname,
                dateOfBirth = "unknown",
                validUntil = "2023-09-01",
                postCity = "unknown",
                postCode = "unknown",
                picture = byteArrayOf(),
            )
        }
    }

    override fun getLifetime(): Duration {
        return lifetime
    }

    data class EcoStudentData(
        val firstname: String,
        val lastname: String
    )

}