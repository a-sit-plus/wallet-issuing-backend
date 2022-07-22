package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.PupilIdCredential
import at.asitplus.wallet.lib.decodeBase64ToArray
import kotlinx.datetime.Instant
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

    override fun getClaim(
        subjectId: String,
        attributeName: String,
        bpk: String,
        maxExpiration: Instant
    ) =
        null // not supported for ECO

    override fun getCredential(
        subjectId: String,
        attributeType: String,
        bpk: String,
        maxExpiration: Instant
    ) = kotlin.runCatching {
        if (attributeType != ConstantIndex.PupilId.vcType)
            return null
        val entity = restTemplate.getForEntity<EcoStudentData>(
            "$url/Student/{bpk}",
            uriVariables = mapOf("bpk" to bpk)
        ).also { log.debug("getCredential for '{}' got {}", bpk, it) }
        val body = entity.body
            ?: return null.also { log.info("getCredential for '{}' returns null: {}", bpk, entity) }
        val (expString, parsedExpiration) = (kotlin.runCatching {
            body.validUntil to Instant.parse(
                body.validUntil
            )
        }.getOrNull()
            ?: kotlin.run {
                log.warn("Could not parse validUtil String ${body.validUntil}, retrying with added time zone")
                "${body.validUntil}Z".let { it to Instant.parse(it) }
            })
        log.debug("Using validUntil String $expString")
        val cappedExpiration =
            if (maxExpiration > parsedExpiration) parsedExpiration else maxExpiration
        if (cappedExpiration != maxExpiration)
            log.info(
                "Capping expiration to '{}', max expiration would be '{}'",
                cappedExpiration,
                maxExpiration
            )
        val subject = PupilIdCredential(
            id = subjectId,
            firstName = body.firstname,
            lastName = body.lastname,
            dateOfBirth = body.dateOfBirth,
            schoolName = body.schoolName,
            schoolCity = body.schoolCity,
            schoolZip = body.schoolZip,
            schoolStreet = body.schoolStreet,
            schoolId = body.schoolId,
            pupilCity = body.studentCity,
            pupilZip = body.studentZip,
            pupilId = body.studentId,
            picture = body.photo.decodeBase64ToArray() ?: byteArrayOf(),
            validUntil = expString
        )
        CredentialDataProvider.CredentialToBeIssued(subject, cappedExpiration, attributeType).also {
            log.info("getCredential for '{}' returns {}", bpk, it)
        }
    }.getOrElse {
        log.error("getCredential for '$bpk' got error", it)
        null
    }

    data class EcoStudentData(
        val firstname: String,
        val lastname: String,
        val dateOfBirth: String,
        val schoolName: String,
        val schoolCity: String,
        val schoolZip: String,
        val schoolStreet: String,
        val schoolId: String,
        val studentCity: String?,
        val studentZip: String?,
        val studentId: String?,
        val photo: String,
        val validUntil: String,
    )

}