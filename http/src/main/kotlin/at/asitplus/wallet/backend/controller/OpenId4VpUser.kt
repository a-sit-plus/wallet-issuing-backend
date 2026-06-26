package at.asitplus.wallet.backend.controller

import at.asitplus.KmmResult
import at.asitplus.openid.IdToken
import at.asitplus.signum.indispensable.io.Base64UrlStrict
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtScheme
import at.asitplus.wallet.lib.agent.Verifier
import at.asitplus.wallet.lib.agent.validation.CredentialFreshnessSummary
import at.asitplus.wallet.lib.agent.validation.CredentialTimelinessValidationSummary
import at.asitplus.wallet.lib.agent.validation.CredentialTimelinessValidationSummary.*
import at.asitplus.wallet.lib.agent.validation.common.EntityExpiredError
import at.asitplus.wallet.lib.agent.validation.common.EntityNotYetValidError
import at.asitplus.wallet.lib.data.CredentialToJsonConverter.toJsonElement
import at.asitplus.wallet.lib.data.IsoDocumentParsed
import at.asitplus.wallet.lib.data.VcJwsVerificationResultWrapper
import at.asitplus.wallet.lib.data.VerifiablePresentationParsed
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusValidationResult
import at.asitplus.wallet.lib.iso.Iso180137AnnexCVerifiedPresentationResult
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.openid.VpTokenValidationResult
import at.asitplus.wallet.lib.openid.VpTokenValidationResultDCQL
import at.asitplus.wallet.lib.openid.VpTokenValidationResultPresentationExchange
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.springframework.security.core.AuthenticatedPrincipal
import java.security.MessageDigest

@Serializable
data class OpenId4VpUser(
    val idToken: IdToken?,
    val idTokenError: String?,
    val credentials: Collection<ParsedCredential>?,
    val presentationError: String?,
) : AuthenticatedPrincipal {
    @Transient
    val id = Json.encodeToString(OpenId4VpUserIdSource(idToken, idTokenError, credentials, presentationError)).sha256()

    @Transient
    val firstname = credentials?.firstNotNullOfOrNull { it.getGivenName() } ?: "N/A"

    @Transient
    val lastname = credentials?.firstNotNullOfOrNull { it.getFamilyName() } ?: "N/A"

    @Transient
    val imageDataBase64 = credentials?.firstNotNullOfOrNull { it.getPortrait() }?.toImage()

    override fun getName(): String = "$firstname $lastname ($id)"

}

@Serializable
private data class OpenId4VpUserIdSource(
    val idToken: IdToken?,
    val idTokenError: String?,
    val credentials: Collection<ParsedCredential>?,
    val presentationError: String?,
)

@Serializable
data class ParsedCredential(
    val jwtCredential: JsonElement? = null,
    val allFields: JsonObject? = null,
    val credentialType: String? = null,
    val error: String? = null,
)

private fun String?.toImage() = this?.let { "data:image;base64,${it.replace("-", "+").replace("_", "/")}" }

private fun ParsedCredential.getPortrait() =
    getClaim(MobileDrivingLicenceDataElements.PORTRAIT)
        ?: getClaim(EuPidSdJwtScheme.SdJwtAttributes.PORTRAIT)
        ?: getClaim(EuPidScheme.Attributes.PORTRAIT)

private fun ParsedCredential.getFamilyName() =
    getClaim(EuPidScheme.Attributes.FAMILY_NAME)
        ?: getClaim(EuPidSdJwtScheme.SdJwtAttributes.FAMILY_NAME)
        ?: getClaim(MobileDrivingLicenceDataElements.FAMILY_NAME)

private fun ParsedCredential.getGivenName() =
    getClaim(EuPidScheme.Attributes.GIVEN_NAME)
        ?: getClaim(EuPidSdJwtScheme.SdJwtAttributes.GIVEN_NAME)
        ?: getClaim(MobileDrivingLicenceDataElements.GIVEN_NAME)

fun ParsedCredential.getClaim(claim: String) = allFields?.entries
    ?.firstOrNull { it.key == claim }?.value
    ?.let {
        when (it) {
            is JsonPrimitive -> it.content
            else -> it.toString()
        }
    }

fun AuthnResponseResult.toUser() = OpenId4VpUser(
    idToken = idTokenValidationResult?.getOrNull(),
    idTokenError = idTokenValidationResult?.exceptionOrNull()?.message,
    credentials = vpTokenValidationResult?.getOrNull()?.presentations()?.flatMap {
        it.toApiItemCredentials()
    },
    presentationError = vpTokenValidationResult?.exceptionOrNull()?.message
        ?: when (val presentation = vpTokenValidationResult?.getOrNull()) {
            is VpTokenValidationResultDCQL -> presentation.submissionRequirementsValidationResult.exceptionOrNull()?.message
            else -> null
        },
)

fun VpTokenValidationResult.presentations() = when (this) {
    is VpTokenValidationResultDCQL -> credentialQueryResponseValidations.flatMap {
        it.value
    }

    is VpTokenValidationResultPresentationExchange -> inputDescriptorResponseValidations.values
}

fun KmmResult<Verifier.VerifyPresentationResult>.toApiItemCredentials() = exceptionOrNull()?.let {
    listOf(ParsedCredential(error = it.message))
} ?: when (val it = getOrThrow()) {
    is Verifier.VerifyPresentationResult.Success -> it.toApiItemCredentials()
    is Verifier.VerifyPresentationResult.SuccessIso -> it.toApiItemCredentials()
    is Verifier.VerifyPresentationResult.SuccessSdJwt -> it.toApiItemCredentials()
    is Verifier.VerifyPresentationResult.SuccessUnsigned -> it.toApiItemCredentials()
}

fun Verifier.VerifyPresentationResult.Success.toApiItemCredentials(): Collection<ParsedCredential> =
    vp.toApiItemCredentials()

fun Iso180137AnnexCVerifiedPresentationResult.toUser() = OpenId4VpUser(
    idToken = null,
    idTokenError = null,
    presentationError = null,
    credentials = documents.map { it.toApiItemCredential() }
)

fun KmmResult<AuthnResponseResult>.convertToUser(): OpenId4VpUser =
    exceptionOrNull()?.let { throw RuntimeException("Failed: input", it) }
        ?: getOrThrow().toUser()

fun VerifiablePresentationParsed.toApiItemCredentials(): List<ParsedCredential> =
    freshVerifiableCredentials.takeIf { it.isNotEmpty() }?.let {
        it.map {
            ParsedCredential(
                jwtCredential = it.vcJws.vc.credentialSubject,
                credentialType = EuPidScheme.vcType,
            )
        }
    } ?: notVerifiablyFreshVerifiableCredentials.takeIf { it.isNotEmpty() }?.let {
        it.map { it.freshnessSummary }
            .map { it.toApiItemCredential() }
    } ?: invalidVerifiableCredentials.takeIf { it.isNotEmpty() }?.let {
        it.map { ParsedCredential(error = "Structure invalid: $it") }
    } ?: listOf(ParsedCredential(error = "No result"))

fun Verifier.VerifyPresentationResult.SuccessUnsigned.toApiItemCredentials(): List<ParsedCredential> =
    vc.toApiItemCredentials()

fun VcJwsVerificationResultWrapper.toApiItemCredentials(): List<ParsedCredential> = if (freshnessSummary.isFresh) {
    listOf(this).map {
        ParsedCredential(
            jwtCredential = it.vcJws.vc.credentialSubject,
            credentialType = it.vcJws.vc.type.first(),
        )
    }
} else {
    listOf(freshnessSummary.toApiItemCredential())
}

fun CredentialFreshnessSummary.VcJws.toApiItemCredential(): ParsedCredential =
    ParsedCredential(error = errorMessage())

fun Verifier.VerifyPresentationResult.SuccessSdJwt.toApiItemCredentials(): Collection<ParsedCredential> = listOf(
    ParsedCredential(
        allFields = reconstructedJsonObject,
        credentialType = verifiableCredentialSdJwt.verifiableCredentialType,
        error = freshnessSummary.errorMessage()
    )
)

private fun CredentialTimelinessValidationSummary.errorMessage(): String? =
    if (isNotYetValid) detailsNotYetValid()
    else if (isExpired) detailsExpired()
    else null

fun CredentialTimelinessValidationSummary.detailsNotYetValid() = when (this) {
    is Mdoc -> details.msoTimelinessValidationSummary?.mdocNotYetValidError?.errorMessage()
    is SdJwt -> details.jwsNotYetValidError?.errorMessage()
    is VcJws -> details.jwsNotYetValidError?.errorMessage()
        ?: details.credentialNotYetValidError?.errorMessage()
}

private fun EntityNotYetValidError.errorMessage(): String =
    "Not yet valid: ${notBeforeTime.formatted()}"

fun CredentialTimelinessValidationSummary.detailsExpired() = when (this) {
    is Mdoc -> details.msoTimelinessValidationSummary?.mdocExpiredError?.errorMessage()
    is SdJwt -> details.jwsExpiredError?.errorMessage()
    is VcJws -> details.jwsExpiredError?.errorMessage()
        ?: details.credentialExpiredError?.errorMessage()
}

private fun EntityExpiredError.errorMessage(): String =
    "Expired at: ${expirationTime.formatted()}"

private fun kotlin.time.Instant.formatted(): String =
    toLocalDateTime(TimeZone.currentSystemDefault()).format(LocalDateTime.Format {
        date(LocalDate.Format { year(); char('-'); monthNumber(); char('-'); day() })
        char(' ')
        time(LocalTime.Format { hour(); char(':'); minute(); char(':'); second() })
    })

fun Verifier.VerifyPresentationResult.SuccessIso.toApiItemCredentials(): Collection<ParsedCredential> =
    documents.map { it.toApiItemCredential() }

private fun IsoDocumentParsed.toApiItemCredential(): ParsedCredential = ParsedCredential(
    allFields = buildJsonObject {
        validItems.forEach {
            put(it.elementIdentifier, it.elementValue.toJsonElement())
        }
    },
    credentialType = mso.docType,
    error = freshnessSummary.errorMessage(),
)

private fun CredentialFreshnessSummary.SdJwt.errorMessage(): String? =
    if (isFresh) null else listOfNotNull(
        tokenStatusValidationResult.errorMessage(),
        timelinessValidationSummary.errorMessage()
    ).takeIf { it.isNotEmpty() }?.joinToString()

private fun CredentialFreshnessSummary.VcJws.errorMessage(): String? =
    if (isFresh) null else listOfNotNull(
        tokenStatusValidationResult.errorMessage(),
        timelinessValidationSummary.errorMessage()
    ).takeIf { it.isNotEmpty() }?.joinToString()

private fun CredentialFreshnessSummary.Mdoc.errorMessage(): String? =
    if (isFresh) null else listOfNotNull(
        tokenStatusValidationResult.errorMessage(),
        timelinessValidationSummary.errorMessage()
    ).takeIf { it.isNotEmpty() }?.joinToString()

private fun TokenStatusValidationResult.errorMessage(): String? = when (this) {
    is TokenStatusValidationResult.Invalid -> "Invalid: Token status is ${this.tokenStatus}"
    is TokenStatusValidationResult.Rejected -> "Rejected: Error is ${this.throwable.toString()}"
    is TokenStatusValidationResult.Valid -> null
}

private fun String.sha256() = runCatching {
    MessageDigest.getInstance("SHA-256").digest(this.encodeToByteArray()).encodeToString(Base64UrlStrict)
}.getOrElse { this.hashCode().toString() }
