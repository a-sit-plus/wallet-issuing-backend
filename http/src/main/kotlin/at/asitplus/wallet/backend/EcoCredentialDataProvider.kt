package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.CredentialSubject
import at.asitplus.wallet.lib.data.PupilIdCredential
import at.asitplus.wallet.lib.decodeBase64ToArray
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity


/**
 * Gets credentials for the currently authenticated user from
 * the connection to the external webservice at ECO,
 * i.e. it looks up data with the `bpk` from a remote service
 */
class EcoCredentialDataProvider(
    private val url: String,
    private val restTemplate: RestTemplate,
) : CredentialDataProvider {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun getClaim(subjectId: String, attributeName: String, bpk: String): CredentialSubject? {
        return null // not supported for ECO
    }

    override fun getCredential(subjectId: String, attributeType: String, bpk: String) = kotlin.runCatching {
        if (attributeType != ConstantIndex.PupilId.vcType)
            return null
        val entity = restTemplate.getForEntity<EcoStudentData>(
            "$url/Student/{bpk}",
            uriVariables = mapOf("bpk" to bpk)
        ).also { log.debug("getCredential for '{}' got {}", bpk, it) }
        val body = entity.body
            ?: return null.also { log.info("getCredential for '{}' returns null: {}", bpk, entity) }
        val credential = PupilIdCredential(
            id = subjectId,
            schoolName = body.schoolName ?: "",
            schoolCity = body.schoolCity ?: "",
            schoolPostCode = body.schoolZip ?: "",
            schoolStreet = body.schoolStreet ?: "",
            schoolNumber = body.schoolId ?: "",
            pupilNumber = body.studentId ?: "",
            firstName = body.firstname ?: "",
            lastName = body.lastname ?: "",
            dateOfBirth = body.dateOfBirth ?: "",
            validUntil = body.validUntil ?: "",
            postCity = body.studentCity ?: "",
            postCode = body.studentZip ?: "",
            picture = body.photo?.decodeBase64ToArray() ?: byteArrayOf(),
        )
        credential.also {
            log.info("getCredential for '{}' returns {}", bpk, it)
        }
    }.getOrElse {
        log.error("getCredential for '$bpk' got error", it)
        null
    }

    data class EcoStudentData(
        val firstname: String?,
        val lastname: String?,
        val dateOfBirth: String?,
        val schoolName: String?,
        val schoolCity: String?,
        val schoolZip: String?,
        val schoolStreet: String?,
        val schoolId: String?,
        val studentCity: String?,
        val studentZip: String?,
        val studentId: String?,
        val validUntil: String?,
        val photo: String?,
    )

}