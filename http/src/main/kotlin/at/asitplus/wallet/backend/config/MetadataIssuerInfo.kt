package at.asitplus.wallet.backend.config

import at.asitplus.etsi.relyingParty.WrpRegistrarDataset
import at.asitplus.openid.IssuerInfo
import at.asitplus.openid.OpenIdConstants
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.core.io.ResourceLoader

/** Load independently optional registration artifacts, preserving the registrar's JSON and signed JWT. */
internal fun loadMetadataIssuerInfo(
    configuration: BackendConfigurationProperties,
    resources: ResourceLoader,
): List<IssuerInfo>? = buildList {
    configuration.metadataRegistrarDataset?.let { location ->
        val json = joseCompliantSerializer.parseToJsonElement(resources.getResource(location).inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        require(json is JsonObject && json.isNotEmpty()) { "Metadata registrar dataset must be a non-empty JSON object" }
        val dataset = joseCompliantSerializer.decodeFromJsonElement(WrpRegistrarDataset.serializer(), json)
        require(dataset.identifier.isNotEmpty()) { "Metadata registrar dataset has no identifier" }
        require(dataset.srvDescription.isNotEmpty()) { "Metadata registrar dataset has no srvDescription" }
        require(dataset.registryURI.isNotBlank()) { "Metadata registrar dataset has no registryURI" }
        require(dataset.providesAttestations.isNotEmpty()) { "Metadata registrar dataset has no providesAttestations" }
        add(IssuerInfo(OpenIdConstants.IssuerInfo.REGISTRAR_DATASET_FORMAT, json))
    }
    configuration.metadataRegistrationCertificate?.let { location ->
        val registration = resources.getResource(location).inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
        require(registration.isNotEmpty()) { "Metadata registration certificate is empty" }
        add(IssuerInfo(OpenIdConstants.IssuerInfo.REGISTRATION_CERT_FORMAT, JsonPrimitive(registration)))
    }
}.takeIf { it.isNotEmpty() }
