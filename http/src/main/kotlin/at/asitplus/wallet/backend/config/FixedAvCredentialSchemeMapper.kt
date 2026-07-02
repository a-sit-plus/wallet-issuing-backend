package at.asitplus.wallet.backend.config

import at.asitplus.openid.SupportedCredentialFormat
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.ISO_MDOC
import at.asitplus.wallet.lib.data.CredentialScheme
import at.asitplus.wallet.lib.data.IsoMdocCredentialScheme
import at.asitplus.wallet.lib.oidvci.CredentialSchemeMapper
import at.asitplus.wallet.lib.oidvci.toIsoMdocSupportedCredentialFormat

/**
 * Uses a fixed scope and credential identifier ([fixedIdentifier]) for the Age Verification credential
 * ([AV_DOCTYPE]) in [CredentialRepresentation.ISO_MDOC], as required by the AV profile. The AV scheme is now
 * resolved from remote metadata, so it is matched by its ISO docType rather than by a library scheme object.
 */
class FixedAvCredentialSchemeMapper(
    val delegate: CredentialSchemeMapper,
    val fixedIdentifier: String = "proof_of_age",
) : CredentialSchemeMapper by delegate {

    private val CredentialScheme.isAv: Boolean
        get() = isoDocType == AV_DOCTYPE

    override fun map(scheme: CredentialScheme): Map<String, SupportedCredentialFormat> =
        if (scheme.isAv && scheme is IsoMdocCredentialScheme)
            mapOf(scheme.toIsoMdocSupportedCredentialFormat(fixedIdentifier))
        else delegate.map(scheme)

    override fun toCredentialIdentifier(scheme: CredentialScheme, rep: CredentialRepresentation) =
        if (rep == ISO_MDOC && scheme.isAv) fixedIdentifier
        else delegate.toCredentialIdentifier(scheme, rep)

    override fun decodeFromCredentialIdentifier(input: String) =
        if (input == fixedIdentifier)
            AttributeIndex.resolveIsoDoctype(AV_DOCTYPE)?.let { it to ISO_MDOC }
        else delegate.decodeFromCredentialIdentifier(input)
}
