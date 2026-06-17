package at.asitplus.wallet.backend.config

import at.asitplus.openid.CredentialFormatEnum.DC_SD_JWT
import at.asitplus.openid.CredentialFormatEnum.JWT_VC
import at.asitplus.openid.SupportedCredentialFormat
import at.asitplus.wallet.ageverification.AgeVerificationScheme
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.data.IsoMdocCredentialScheme
import at.asitplus.wallet.lib.data.SdJwtCredentialScheme
import at.asitplus.wallet.lib.data.VcJwtCredentialScheme
import at.asitplus.wallet.lib.oidvci.CredentialSchemeMapper
import at.asitplus.wallet.lib.oidvci.toIsoMdocSupportedCredentialFormat
import at.asitplus.wallet.lib.oidvci.toPlainJwtSupportedCredentialFormat
import at.asitplus.wallet.lib.oidvci.toSdJwtSupportedCredentialFormat

/**
 * Uses a fixed scope and credential identifier for [AgeVerificationScheme] in [CredentialRepresentation.ISO_MDOC].
 */
class FixedAvCredentialSchemeMapper(
    val delegate: CredentialSchemeMapper,
    val fixedIdentifier: String = "proof_of_age",
) : CredentialSchemeMapper by delegate {

    override fun map(scheme: at.asitplus.wallet.lib.data.CredentialScheme): Map<String, SupportedCredentialFormat> =
        listOfNotNull(
            if (scheme is IsoMdocCredentialScheme) scheme.toIsoMdocSupportedCredentialFormat(
                toCredentialIdentifier(scheme, CredentialRepresentation.ISO_MDOC)
            ) else null,
            if (scheme is VcJwtCredentialScheme) scheme.toPlainJwtSupportedCredentialFormat(
                toCredentialIdentifier(scheme, CredentialRepresentation.PLAIN_JWT)
            ) else null,
            if (scheme is SdJwtCredentialScheme) scheme.toSdJwtSupportedCredentialFormat(
                toCredentialIdentifier(scheme, CredentialRepresentation.SD_JWT)
            ) else null
        ).toMap()

    override fun toCredentialIdentifier(
        scheme: at.asitplus.wallet.lib.data.CredentialScheme,
        rep: CredentialRepresentation,
    ) = when (rep) {
        CredentialRepresentation.PLAIN_JWT -> encodeToCredentialIdentifier(scheme.vcType!!, JWT_VC)
        CredentialRepresentation.SD_JWT -> encodeToCredentialIdentifier(scheme.sdJwtType!!, DC_SD_JWT)
        CredentialRepresentation.ISO_MDOC -> if (scheme == AgeVerificationScheme) fixedIdentifier else scheme.isoNamespace!!
    }

    override fun decodeFromCredentialIdentifier(
        input: String,
    ) = if (input == fixedIdentifier)
        AgeVerificationScheme to CredentialRepresentation.ISO_MDOC
    else
        delegate.decodeFromCredentialIdentifier(input)
}
