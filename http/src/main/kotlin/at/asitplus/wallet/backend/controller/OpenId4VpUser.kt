package at.asitplus.wallet.backend.controller

import at.asitplus.openid.dcql.DCQLCredentialQueryIdentifier
import at.asitplus.signum.indispensable.io.Base64UrlStrict
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtScheme
import at.asitplus.wallet.lib.data.CredentialToJsonConverter.toJsonElement
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.openid.AuthnResponseResult.*
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.springframework.security.core.AuthenticatedPrincipal
import java.security.MessageDigest

@Serializable
class OpenId4VpUser(
    val id: String,
    val firstname: String,
    val lastname: String,
    val imageDataBase64: String?,
    val credentials: List<ParsedCredential>,
) : AuthenticatedPrincipal {

    override fun getName(): String = "$firstname $lastname ($id)"

    override fun toString(): String = "OpenId4VpUser(credentials=$credentials)"

}

@Serializable
data class ParsedCredential(
    val allFields: JsonObject,
    val credentialType: String,
)


fun List<ParsedCredential>.toOpenId4VpUser() = OpenId4VpUser(
    id = Json.encodeToString(this).sha256(),
    firstname = firstNotNullOfOrNull { it.getGivenName() } ?: "N/A",
    lastname = firstNotNullOfOrNull { it.getFamilyName() } ?: "N/A",
    imageDataBase64 = firstNotNullOfOrNull { it.getPortrait() }?.toImage(),
    credentials = this
)

fun ParsedCredential.toOpenId4VpUser() = OpenId4VpUser(
    id = Json.encodeToString(this).sha256(),
    firstname = getGivenName() ?: "N/A",
    lastname = getFamilyName() ?: "N/A",
    imageDataBase64 = getPortrait()?.toImage(),
    credentials = listOf(this)
)

private fun String?.toImage() = this?.let { "data:image;base64,${it.ensureBase64Encoding()}" }

private fun String.ensureBase64Encoding(): String = replace("-", "+").replace("_", "/")

private fun ParsedCredential.getPortrait() =
    getClaim(EuPidScheme.Attributes.PORTRAIT)
        ?: getClaim(EuPidSdJwtScheme.SdJwtAttributes.PORTRAIT)
        ?: getClaim(MobileDrivingLicenceDataElements.PORTRAIT)

private fun ParsedCredential.getFamilyName() =
    getClaim(EuPidScheme.Attributes.FAMILY_NAME)
        ?: getClaim(EuPidSdJwtScheme.SdJwtAttributes.FAMILY_NAME)
        ?: getClaim(MobileDrivingLicenceDataElements.FAMILY_NAME)

private fun ParsedCredential.getGivenName() =
    getClaim(EuPidScheme.Attributes.GIVEN_NAME)
        ?: getClaim(EuPidSdJwtScheme.SdJwtAttributes.GIVEN_NAME)
        ?: getClaim(MobileDrivingLicenceDataElements.GIVEN_NAME)

fun ParsedCredential.getClaim(claim: String) = this.allFields.entries
    .firstOrNull { it.key == claim }?.value?.let {
        when (it) {
            is JsonPrimitive -> it.content
            else -> it.toString()
        }
    }

fun VerifiablePresentationValidationResults.toOpenId4VpUser(): OpenId4VpUser = toParsedCredential().toOpenId4VpUser()

fun VerifiablePresentationValidationResults.toParsedCredential(): List<ParsedCredential> =
    validationResults.flatMap {
        it.toParsedCredential()
    }

fun Map<DCQLCredentialQueryIdentifier, List<AuthnResponseResult>>.toParsedCredential(): List<ParsedCredential> =
    values.flatMap {
        it.flatMap { it.toParsedCredential() }
    }

private fun AuthnResponseResult.toParsedCredential(): Collection<ParsedCredential> = when (this) {
    is Error -> listOf()
    is IdToken -> listOf()
    is Success -> listOf()
    is ValidationError -> listOf()
    is SuccessIso -> this.toParsedCredential()
    is SuccessSdJwt -> this.toParsedCredentials()
    is VerifiablePresentationValidationResults -> this.toParsedCredential()
    is VerifiableDCQLPresentationValidationResults -> this.toParsedCredential()
}

fun VerifiableDCQLPresentationValidationResults.toParsedCredential(): Collection<ParsedCredential> =
    allValidationResults.toParsedCredential()

fun VerifiableDCQLPresentationValidationResults.toOpenId4VpUser(): OpenId4VpUser =
    allValidationResults.toParsedCredential().toOpenId4VpUser()

fun SuccessSdJwt.toOpenId4VpUser(): OpenId4VpUser = toParsedCredential().toOpenId4VpUser()

fun SuccessSdJwt.toParsedCredentials(): Collection<ParsedCredential> = listOf(toParsedCredential())

fun SuccessSdJwt.toParsedCredential(): ParsedCredential =
    ParsedCredential(
        allFields = reconstructed,
        credentialType = verifiableCredentialSdJwt.verifiableCredentialType,
    )

fun SuccessIso.toOpenId4VpUser(): OpenId4VpUser = toParsedCredential().toOpenId4VpUser()

fun SuccessIso.toParsedCredential(): List<ParsedCredential> = documents.map { doc ->
    ParsedCredential(
        allFields = buildJsonObject {
            doc.validItems.forEach {
                put(it.elementIdentifier, it.elementValue.toJsonElement())
            }
        },
        credentialType = doc.mso.docType,
    )
}

private fun String.sha256() = runCatching {
    MessageDigest.getInstance("SHA-256").digest(this.encodeToByteArray()).encodeToString(Base64UrlStrict)
}.getOrElse { this.hashCode().toString() }

