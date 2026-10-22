package at.asitplus.wallet.backend.controller

import at.asitplus.signum.indispensable.io.Base64UrlStrict
import at.asitplus.wallet.eupid.EuPidDataElements
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtDataElements
import at.asitplus.wallet.lib.data.CredentialToJsonConverter.toJsonElement
import at.asitplus.wallet.lib.data.VcDataModelConstants
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.validation.ValidatedCredential
import at.asitplus.wallet.lib.validation.ValidationDecision
import at.asitplus.wallet.lib.validation.firstRejection
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.security.core.AuthenticatedPrincipal

@Serializable
data class OpenId4VpUser(
    val credentials: Collection<ParsedCredential>?,
    val presentationError: String?,
) : AuthenticatedPrincipal {
    @Transient
    val id = Json.encodeToString(OpenId4VpUserIdSource(credentials, presentationError)).sha256()

    @Transient
    val firstname = credentials?.firstNotNullOfOrNull { it.getGivenName() }

    @Transient
    val lastname = credentials?.firstNotNullOfOrNull { it.getFamilyName() }

    @Transient
    val imageDataBase64 = credentials?.firstNotNullOfOrNull { it.getPortrait() }?.toImage()

    val isAccepted: Boolean
        get() = presentationError == null && !credentials.isNullOrEmpty() && credentials.all { it.error == null }

    override fun getName(): String =
        (listOfNotNull(firstname, lastname).joinToString(" ") + " ($id)").trim()

}

@Serializable
private data class OpenId4VpUserIdSource(
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

// mdl/eupid use "portrait"; eupid SD-JWT uses "picture"
private fun ParsedCredential.getPortrait() = getClaim(EuPidDataElements.PORTRAIT)
    ?: getClaim(EuPidSdJwtDataElements.PORTRAIT)
    ?: getClaim(MobileDrivingLicenceDataElements.PORTRAIT)

private fun ParsedCredential.getFamilyName() = getClaim(EuPidDataElements.FAMILY_NAME)
    ?: getClaim(EuPidSdJwtDataElements.FAMILY_NAME)
    ?: getClaim(MobileDrivingLicenceDataElements.FAMILY_NAME)

private fun ParsedCredential.getGivenName() = getClaim(EuPidDataElements.GIVEN_NAME)
    ?: getClaim(EuPidSdJwtDataElements.GIVEN_NAME)
    ?: getClaim(MobileDrivingLicenceDataElements.GIVEN_NAME)

fun ParsedCredential.getClaim(claim: String) = allFields?.entries
    ?.firstOrNull { it.key == claim }?.value
    ?.let {
        when (it) {
            is JsonPrimitive -> it.content
            else -> it.toString()
        }
    }

/** Only accepted validation results may supply an authenticated identity. */
fun AuthnResponseResult.toUser(): OpenId4VpUser = when (this) {
    is AuthnResponseResult.Error -> OpenId4VpUser(null, error.errorDescription ?: error.error)
    is AuthnResponseResult.Success -> {
        val presentations = presentationValidations.values.flatten()
        if (validationReport.decision != ValidationDecision.ACCEPTED) {
            OpenId4VpUser(null, validationReport.firstRejection()?.message
                ?: "PID presentation rejected")
        } else {
            val credentials = presentations.flatMap { it.presentation?.credentials.orEmpty() }.map {
                val json = toJsonElement(it)
                ParsedCredential(
                    allFields = if (it is ValidatedCredential.IsoMdoc) JsonObject(
                        json.values.filterIsInstance<JsonObject>().flatMap { it.entries }.associate { it.toPair() }
                    ) else json,
                    credentialType = it.credentialIdentifier,
                )
            }
            if (credentials.isEmpty()) OpenId4VpUser(null, "No validated PID credentials")
            else OpenId4VpUser(credentials, null)
        }
    }
}

/**
 * The signed type of this credential: the `vct` of an SD-JWT VC, the `docType` of an ISO mdoc, or the only type of a
 * VC-JWT other than `VerifiableCredential`, `null` if it has several.
 */
private val ValidatedCredential.credentialIdentifier: String?
    get() = when (this) {
        is ValidatedCredential.SdJwtVc -> credential.verifiableCredentialType
        is ValidatedCredential.IsoMdoc -> issuerSigned.issuerAuth.payload?.docType
        is ValidatedCredential.VcJwt -> credential.vc.type
            .filter { it != VcDataModelConstants.VERIFIABLE_CREDENTIAL }
            .singleOrNull()
    }

private fun String.sha256() = runCatching {
    MessageDigest.getInstance("SHA-256").digest(this.encodeToByteArray()).encodeToString(Base64UrlStrict)
}.getOrElse { this.hashCode().toString() }
