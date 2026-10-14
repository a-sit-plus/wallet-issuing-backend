package at.asitplus.wallet.backend.config

import at.asitplus.etsi.relyingParty.WrpRegistrarDataset
import at.asitplus.openid.IssuerInfo
import at.asitplus.openid.OpenIdConstants
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import org.springframework.core.io.ResourceLoader

private val log = LoggerFactory.getLogger("at.asitplus.wallet.backend.config.MetadataIssuerInfo")

/** Load independently optional registration artifacts, preserving the registrar's JSON and signed JWT. */
internal fun loadMetadataIssuerInfo(
    configuration: BackendConfigurationProperties,
    resources: ResourceLoader,
): List<IssuerInfo>? = buildList {
    configuration.metadataRegistrarDataset?.let { location ->
        val json = joseCompliantSerializer.parseToJsonElement(resources.getResource(location).inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        add(IssuerInfo(OpenIdConstants.IssuerInfo.REGISTRAR_DATASET_FORMAT, json.registrarDataset().also { it.warnIfIncompatible() }))
    }
    configuration.metadataRegistrationCertificate?.let { location ->
        val registration = resources.getResource(location).inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
        require(registration.isNotEmpty()) { "Metadata registration certificate is empty" }
        add(IssuerInfo(OpenIdConstants.IssuerInfo.REGISTRATION_CERT_FORMAT, JsonPrimitive(registration)))
    }
}.takeIf { it.isNotEmpty() }

/**
 * The dataset object itself, or the `data` of the single `registrar_dataset` element of an `issuer_info`
 * array (or of one such element), as exported by registrars.
 */
private fun JsonElement.registrarDataset(): JsonObject {
    val entries = (this as? JsonArray ?: JsonArray(listOf(this))).map {
        require(it is JsonObject) { "Metadata registrar dataset must be a JSON object or an issuer_info array" }
        it
    }
    val wrapped = entries.filter { it.isIssuerInfo() }
    val dataset = when {
        wrapped.isEmpty() && this is JsonObject -> this
        wrapped.isEmpty() -> throw IllegalArgumentException("Metadata registrar dataset contains no registrar_dataset element")
        else -> wrapped.filter { (it["format"] as JsonPrimitive).content == OpenIdConstants.IssuerInfo.REGISTRAR_DATASET_FORMAT }
            .also { require(it.size == 1) { "Metadata registrar dataset contains ${it.size} registrar_dataset elements, not one" } }
            .single()["data"]
    }
    require(dataset is JsonObject && dataset.isNotEmpty()) { "Metadata registrar dataset must be a non-empty JSON object" }
    return dataset
}

private fun JsonObject.isIssuerInfo() = containsKey("data") && (this["format"] as? JsonPrimitive)?.isString == true

/** Published unchanged, but VC-K's issuer metadata validator requires the ETSI TS 119 472-3 V1.1.1 dataset members. */
private fun JsonObject.warnIfIncompatible() {
    runCatching { joseCompliantSerializer.decodeFromJsonElement(WrpRegistrarDataset.serializer(), this) }
        .onFailure { log.warn("Metadata registrar dataset does not match WrpRegistrarDataset, clients may reject it: {}", it.message) }
        .onSuccess {
            if (it.identifier.isEmpty() || it.srvDescription.isEmpty() || it.registryURI.isBlank() || it.providesAttestations.isEmpty())
                log.warn("Metadata registrar dataset has empty identifier, srvDescription, registryURI or providesAttestations, clients may reject it")
        }
}
