package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.wallet.backend.Extensions.sha256
import at.asitplus.wallet.backend.data.CredentialDataProvider.CredentialToBeIssuedAttachment
import at.asitplus.wallet.lib.DataSourceProblem
import at.asitplus.wallet.pupilid.ConstantIndex
import at.asitplus.wallet.pupilid.PupilIdCredential
import io.github.aakira.napier.Napier
import io.matthewnelson.component.base64.decodeBase64ToArray
import io.matthewnelson.component.encoding.base16.encodeBase16
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import kotlin.time.Duration


/**
 * Gets credentials for the currently authenticated user from
 * the connection to the external webservice at ECO,
 * i.e. it looks up data with the `bpk` from a remote service
 */
class EcoCredentialDataProvider(
    private val url: String,
    private val restTemplate: RestTemplate,
    private val gracePeriod: Duration,
    private val pictureService: PictureService,
) : CredentialDataProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getClaim(
        subjectId: String,
        attributeName: String,
        bpk: String,
        maxExpiration: Instant
    ): KmmResult<CredentialDataProvider.CredentialToBeIssued> =
        KmmResult.failure(UnsupportedOperationException("ECO does not support claims"))

    override fun getCredential(
        subjectId: String,
        attributeType: String,
        bpk: String,
        maxExpiration: Instant
    ): KmmResult<CredentialDataProvider.CredentialToBeIssued> = kotlin.runCatching {
        if (attributeType != ConstantIndex.PupilId.vcType)
            return KmmResult.failure(UnsupportedOperationException("Unsupported attribute type '$attributeType"))
        val entity = restTemplate.getForEntity<EcoStudentData>(
            "$url/Student/{bpk}",
            uriVariables = mapOf("bpk" to bpk)
        ).also { Napier.v("getCredential for '$bpk' got $it") }
        val body = entity.body
            ?: Napier.v("getCredential for '$bpk' returns null: $entity")
                .run { return KmmResult.failure(NullPointerException("getCredential for '$bpk' returns null: $entity")) }
        val parsedExpiration = LenientInstantParser.parse(body.validUntil)
            ?: Napier.v("getCredential for '$bpk' could not parse validUntil: '${body.validUntil}'")
                .run { return KmmResult.failure(NullPointerException("Could not parse validUntil: '${body.validUntil}'")) }
        Napier.v("Using validUntil $parsedExpiration")
        val cappedExpiration = if (maxExpiration > parsedExpiration) parsedExpiration else maxExpiration
        if (cappedExpiration != maxExpiration) {
            Napier.i("Capping expiration")
            Napier.v("Capping expiration to '$cappedExpiration', max expiration would be '$maxExpiration'")
        }
        val validUntilString = LenientInstantParser.toYearMonthDateString(cappedExpiration)
        val picture = body.photo.decodeBase64ToArray() ?: {
            Napier.i("No photo from ECO, return failure")
            Napier.v("No photo from ECO, return failure, for bpk '$bpk'")
        }.run { return KmmResult.failure(DataSourceProblem("Photo could not be decoded")) }
        if (picture.take(2).toByteArray().encodeBase16().uppercase() != "FFD8") {
            Napier.w("ECO sent no JPG as picture, aborting")
            return KmmResult.failure(DataSourceProblem("Photo is not in JPG format"))
        }
        val pictureHash = picture.sha256()
        val scaledPicture = pictureService.convertPicture(picture)
        val scaledPictureHash = scaledPicture.sha256()
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
            cardId = body.cardId,
            validUntil = validUntilString,
            pictureHash = pictureHash,
            scaledPictureHash = scaledPictureHash,
        )
        val limit = parsedExpiration + gracePeriod
        val capped = if (maxExpiration > limit) limit else maxExpiration
        val attachments = listOf(
            CredentialToBeIssuedAttachment("picture.jpg", "image/jpg", picture),
            CredentialToBeIssuedAttachment(
                "scaledPicture.${pictureService.extension}",
                pictureService.mediaType,
                scaledPicture
            ),
        )
        val result = CredentialDataProvider.CredentialToBeIssued(subject, capped, attributeType, attachments)

        KmmResult.success(result)
            .also { Napier.v("getCredential for '$bpk' returns ${it.value.toLogString()}") }
            .also { Napier.i("getCredential success") }
    }.getOrElse {
        Napier.e("getCredential for bpk got error")
        Napier.v("bpk: $bpk, error: ", it)

        if (it is HttpStatusCodeException) {
            val problem = kotlin.runCatching { json.decodeFromString<Rfc7807Problem>(it.responseBodyAsString) }
                .getOrElse { _ -> return KmmResult.failure(it) }
            return KmmResult.failure(DataSourceProblem(problem.title, problem.detail, it))
        }
        return KmmResult.failure(it)
    }

    /**
     * Updated from ECO Swagger on 2022-02-05
     */
    data class EcoStudentData(
        val validUntil: String,
        val cardId: String,
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
        val photo: String,
    )

}
