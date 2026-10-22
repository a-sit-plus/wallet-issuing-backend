package at.asitplus.wallet.backend.config

import at.asitplus.openid.IssuerInfo
import at.asitplus.openid.OpenIdConstants
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.core.io.ResourceLoader

/** Load the optional registration certificate, preserving the registrar's signed JWT. */
internal fun loadMetadataIssuerInfo(
    configuration: BackendConfigurationProperties,
    resources: ResourceLoader,
): List<IssuerInfo>? = configuration.metadataRegistrationCertificate?.let { location ->
    val registration = resources.getResource(location).inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
    require(registration.isNotEmpty()) { "Metadata registration certificate is empty" }
    listOf(IssuerInfo(OpenIdConstants.IssuerInfo.REGISTRATION_CERT_FORMAT, JsonPrimitive(registration)))
}
