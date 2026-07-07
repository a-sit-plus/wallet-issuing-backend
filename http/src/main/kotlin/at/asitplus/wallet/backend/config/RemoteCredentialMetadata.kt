package at.asitplus.wallet.backend.config

import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.ISO_MDOC
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.SD_JWT
import at.asitplus.wallet.lib.data.CredentialMetadataLookup
import at.asitplus.wallet.lib.data.CredentialScheme
import at.asitplus.wallet.lib.ktor.openid.RemoteCredentialMetadataRegistry
import at.asitplus.wallet.sdjwt.SdJwtTypeMetadata
import at.asitplus.wallet.sdjwt.SdJwtVcType
import io.ktor.client.*
import io.ktor.client.engine.cio.CIO
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.time.Clock

/** ISO docType / namespace of the Age Verification credential; needs the fixed `proof_of_age` config id. */
const val AV_DOCTYPE = "eu.europa.ec.av.1"

/**
 * The credential type metadata documents we offer, hosted in
 * `a-sit-plus/credentials-collection@feature/type-metadata`. Schemes (claims, display, format) are resolved
 * remotely from these raw URLs at boot — this project no longer depends on the per-credential libraries.
 */
object CredentialDocs {
    const val BASE = "https://raw.githubusercontent.com/a-sit-plus/credentials-collection/main"

    /** [isoDocType] is set for `mso_mdoc` docs, whose lookup identifier is the docType rather than the vct. */
    data class Doc(
        val vct: String,
        val file: String,
        val representation: CredentialRepresentation,
        val isoDocType: String? = null,
    ) {
        val url get() = "$BASE/$file"

        /** The identifier the issuer/wallet uses to request this credential (vct for SD-JWT, docType for mdoc). */
        val identifier get() = isoDocType ?: vct
    }

    val all = listOf(
        Doc("urn:eudi:pid:1", "eu-pid-sdjwt.json", SD_JWT),
        Doc("EuPid2023", "eu-pid.json", ISO_MDOC, isoDocType = "eu.europa.ec.eudi.pid.1"),
        Doc("org.iso.18013.5.1.mDL", "mdl.json", ISO_MDOC, isoDocType = "org.iso.18013.5.1.mDL"),
        Doc("urn:eu.europa.ec.eudi:por:1", "power-of-representation.json", SD_JWT),
        Doc("urn:eu.europa.ec.eudi:tax:1", "tax-id-credential.json", SD_JWT),
        Doc("eu.europa.ec.eudi.cor.1", "certificate-of-residence.json", SD_JWT),
        Doc("urn:eudi:ehic:1", "ehic.json", SD_JWT),
        Doc(AV_DOCTYPE, "age-verification.json", ISO_MDOC, isoDocType = AV_DOCTYPE),
    )
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

/**
 * Fetches the SD-JWT Type Metadata documents live from the hosted collection. Tests override this with a
 * [io.ktor.client.engine.mock.MockEngine] serving the cached documents from test resources
 * (see `CachedTypeMetadataConfiguration` in the test sources), so no test ever talks to GitHub.
 */
@Configuration
class MetadataHttpClientConfiguration {
    @Bean
    fun metadataHttpClient(): HttpClient = HttpClient(CIO)
}

fun buildRemoteRegistry(httpClient: HttpClient) = RemoteCredentialMetadataRegistry(
    httpClient = httpClient,
    clock = Clock.System,
    documentUrls = CredentialDocs.all.associate { SdJwtVcType(it.vct) to it.url }.toMutableMap(),
    // mso_mdoc docs are looked up by docType, so alias (ISO_MDOC, docType) -> vct.
    aliases = CredentialDocs.all.filter { it.isoDocType != null }.associate {
        CredentialMetadataLookup(it.representation, it.isoDocType!!) to SdJwtVcType(it.vct)
    },
)
