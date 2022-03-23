package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.AtomicAttributeCredential
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.CredentialSubject
import at.asitplus.wallet.lib.data.PupilIdCredential
import at.asitplus.wallet.lib.data.SchemaIndex
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import kotlin.time.Duration


class ExternalCredentialDataProvider constructor(
    private val lifetime: Duration,
    private val url: String,
    private val restTemplate: RestTemplate,
) : IssuerCredentialDataProvider {

    override fun getClaim(subjectId: String, attributeName: String): CredentialSubject? {
        return when {
            attributeName.startsWith(SchemaIndex.ATTR_GENERIC_PREFIX) -> {
                val studentData = restTemplate.getForEntity<QuartoStudent>(
                    "$url/Student/{bpk}",
                    mapOf("bpk" to subjectId)
                )
                studentData.body?.let {
                    when (attributeName.removePrefix(SchemaIndex.ATTR_GENERIC_PREFIX + "/")) {
                        "vorname" -> AtomicAttributeCredential(subjectId, attributeName, it.firstname)
                        "nachname" -> AtomicAttributeCredential(subjectId, attributeName, it.lastname)
                        else -> null
                    }
                }
            }
            attributeName.startsWith(SchemaIndex.ATTR_GREEN_PASS_PREFIX) -> {
                return null
            }
            else -> null
        }
    }

    override fun getCredential(subjectId: String, attributeType: String): CredentialSubject? {
        return when (attributeType) {
            ConstantIndex.PupilId.vcType -> {
                // TODO where to get the bpk from?
                // its not the subjectId...
                val studentData = restTemplate.getForEntity<QuartoStudent>(
                    "$url/Student/{bpk}",
                    mapOf("bpk" to subjectId)
                )
                studentData.body?.let {
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
            else -> {
                null
            }
        }
    }

    override fun getLifetime(): Duration {
        return lifetime
    }

    data class QuartoStudent(
        val firstname: String,
        val lastname: String
    )

}