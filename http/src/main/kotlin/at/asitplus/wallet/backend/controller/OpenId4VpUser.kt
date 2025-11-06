package at.asitplus.wallet.backend.controller

import at.asitplus.openid.dcql.DCQLCredentialQueryIdentifier
import at.asitplus.signum.indispensable.io.Base64UrlStrict
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.lib.data.CredentialToJsonConverter.toJsonElement
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.openid.AuthnResponseResult.*
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.springframework.security.core.AuthenticatedPrincipal
import java.security.MessageDigest
import java.time.Instant

@Serializable
class OpenId4VpUser(
    val apiItem: ApiItem,
) : AuthenticatedPrincipal {

    override fun getName(): String = "${apiItem.firstname} ${apiItem.lastname} (${apiItem.id})"

    override fun toString(): String = "OpenId4VpUser(apiItem=$apiItem)"

}

fun List<ApiItemCredential>.toOpenId4VpUser() = OpenId4VpUser(
    apiItem = ApiItem(
        id = Json.encodeToString(this).sha256(),
        firstname = firstNotNullOfOrNull { it.getGivenName() } ?: "N/A",
        lastname = firstNotNullOfOrNull { it.getFamilyName() } ?: "N/A",
        imageDataBase64 = firstNotNullOfOrNull { it.getPortrait() }?.toImage(),
        timestamp = Instant.now().toEpochMilli(),
        credentials = this
    )
)

fun ApiItemCredential.toOpenId4VpUser() = OpenId4VpUser(
    apiItem = ApiItem(
        id = Json.encodeToString(this).sha256(),
        firstname = getGivenName() ?: "N/A",
        lastname = getFamilyName() ?: "N/A",
        imageDataBase64 = getPortrait()?.toImage(),
        timestamp = Instant.now().toEpochMilli(),
        credentials = listOf(this)
    )
)

private fun String?.toImage() = this?.let { "data:image;base64,${it.replace("-", "+").replace("_", "/")}" }

private fun ApiItemCredential.getPortrait() = getClaim(MobileDrivingLicenceDataElements.PORTRAIT)
    ?: getClaim(EuPidScheme.Attributes.PORTRAIT)

private fun ApiItemCredential.getFamilyName() = getClaim(EuPidScheme.Attributes.FAMILY_NAME)
    ?: getClaim(MobileDrivingLicenceDataElements.FAMILY_NAME)

private fun ApiItemCredential.getGivenName() = getClaim(EuPidScheme.Attributes.GIVEN_NAME)
    ?: getClaim(MobileDrivingLicenceDataElements.GIVEN_NAME)

fun ApiItemCredential.getClaim(claim: String) = this.allFields?.entries?.firstOrNull { it.key == claim }?.value?.let {
    when (it) {
        is JsonPrimitive -> it.content
        else -> it.toString()
    }
}

fun VerifiablePresentationValidationResults.toApiItem(): List<ApiItemCredential> =
    validationResults.flatMap {
        when (it) {
            is Error -> listOf()
            is IdToken -> listOf()
            is Success -> listOf()
            is SuccessIso -> it.toApiItem()
            is SuccessSdJwt -> listOf(it.toApiItemCredential())
            is ValidationError -> listOf()
            is VerifiablePresentationValidationResults -> it.toApiItem()
            is VerifiableDCQLPresentationValidationResults -> it.validationResults.toApiItem()
        }
    }

fun Map<DCQLCredentialQueryIdentifier, AuthnResponseResult>.toApiItem(): List<ApiItemCredential> =
    values.flatMap {
        when (it) {
            is Error -> listOf()
            is IdToken -> listOf()
            is Success -> listOf()
            is SuccessIso -> it.toApiItem()
            is SuccessSdJwt -> listOfNotNull(it.toApiItemCredential())
            is ValidationError -> listOf()
            is VerifiableDCQLPresentationValidationResults -> it.validationResults.toApiItem()
            is VerifiablePresentationValidationResults -> listOfNotNull(it.toApiItem())
        }
    }.filterIsInstance<ApiItemCredential>()

fun Map<DCQLCredentialQueryIdentifier, AuthnResponseResult>.toOpenId4VpUser(): OpenId4VpUser =
    this.toApiItem().toOpenId4VpUser()

fun SuccessSdJwt.toApiItemCredential(): ApiItemCredential =
    ApiItemCredential(
        allFields = reconstructed,
        credentialType = verifiableCredentialSdJwt.verifiableCredentialType,
    )

fun SuccessIso.toApiItem(): List<ApiItemCredential> = documents.map { doc ->
    ApiItemCredential(
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

