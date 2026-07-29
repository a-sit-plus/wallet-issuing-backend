package at.asitplus.wallet.backend.config

import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.ISO_MDOC
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.SD_JWT
import at.asitplus.wallet.lib.data.CredentialMetadataLookup
import at.asitplus.wallet.lib.data.CredentialScheme
import at.asitplus.wallet.sdjwt.SdJwtTypeMetadata
import at.asitplus.wallet.sdjwt.SdJwtVcType

/** ISO docType / namespace of the Age Verification credential; needs the fixed `proof_of_age` config id. */
const val AV_DOCTYPE = "eu.europa.ec.av.1"

/**
 * The credential type metadata documents we offer. Most are resolved remotely from
 * `a-sit-plus/credentials-collection`; entries with a `classpath:` URL are bundled with this project.
 */
object CredentialCatalog {
    const val BASE_URL = "https://raw.githubusercontent.com/a-sit-plus/credentials-collection/main"

    /**
     * @param vct the document's `vct` value, used as the registry key
     * @param fileName document file name within the collection
     * @param representation how this credential is requested and presented
     * @param isoDocType for ISO mdoc credentials, the `vck.isoDocType` used as the lookup identifier
     */
    data class Entry(
        val vct: String,
        val fileName: String,
        val representation: CredentialRepresentation,
        val isoDocType: String? = null,
        val url: String = "$BASE_URL/$fileName",
    ) {
        /** The identifier the issuer/wallet uses to request this credential (vct for SD-JWT, docType for mdoc). */
        val identifier get() = isoDocType ?: vct
    }

    val entries = listOf(
        Entry("urn:eudi:pid:1", "eu-pid-sdjwt.json", SD_JWT),
        Entry("EuPid2023", "eu-pid.json", ISO_MDOC, isoDocType = "eu.europa.ec.eudi.pid.1"),
        Entry("org.iso.18013.5.1.mDL", "mdl.json", ISO_MDOC, isoDocType = "org.iso.18013.5.1.mDL"),
        Entry("urn:eu.europa.ec.eudi:por:1", "power-of-representation.json", SD_JWT),
        Entry("urn:eu.europa.ec.eudi:tax:1", "tax-id-credential.json", SD_JWT),
        Entry("eu.europa.ec.eudi.cor.1", "certificate-of-residence.json", SD_JWT),
        Entry("urn:eudi:ehic:1", "ehic.json", SD_JWT),
        Entry(AV_DOCTYPE, "age-verification.json", ISO_MDOC, isoDocType = AV_DOCTYPE),
        Entry(
            Ida15BindingClaims.VCT,
            "ida-15-binding.json",
            SD_JWT,
            url = "classpath:credential-metadata/ida-15-binding.json",
        ),
    )

    /** `vct` -> hosted document URL for remotely loaded entries. */
    fun documentUrls(): MutableMap<SdJwtVcType, String> =
        entries.filterNot { it.url.startsWith("classpath:") }
            .associate { SdJwtVcType(it.vct) to it.url }
            .toMutableMap()

    val localEntries get() = entries.filter { it.url.startsWith("classpath:") }

    /** ISO mdoc docTypes have no direct `vct` fallback, so alias each to its document's `vct`. */
    fun aliases(): Map<CredentialMetadataLookup, SdJwtVcType> =
        entries.filter { it.representation == ISO_MDOC }
            .associate { CredentialMetadataLookup(it.representation, it.identifier) to SdJwtVcType(it.vct) }
}

/** A credential the issuer offers, with display info taken from its remote type metadata document. */
data class CredentialOffering(
    val scheme: CredentialScheme,
    val representation: CredentialRepresentation,
    val name: String,
    val description: String?,
)

private fun SdJwtTypeMetadata?.displayFor(locale: String = "en-US") =
    this?.display?.let { d -> d.firstOrNull { it.locale.string == locale } ?: d.firstOrNull() }

fun SdJwtTypeMetadata?.displayName(fallback: String) = displayFor()?.name ?: this?.name ?: fallback

fun SdJwtTypeMetadata?.displayDescription() = displayFor()?.description ?: this?.description
