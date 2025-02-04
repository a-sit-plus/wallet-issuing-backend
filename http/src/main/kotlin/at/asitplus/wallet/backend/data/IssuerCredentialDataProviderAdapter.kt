package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.catching
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.wallet.backend.config.*
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.oidvci.CredentialIssuerDataProvider
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration


class OidcIssuerCredentialDataProvider(
    private val lifetime: Duration,
    private val ePrescriptionLoader: EPrescriptionLoader,
) : CredentialIssuerDataProvider {
    override fun getCredential(
        userInfo: OidcUserInfoExtended,
        subjectPublicKey: CryptoPublicKey,
        credentialScheme: ConstantIndex.CredentialScheme,
        representation: ConstantIndex.CredentialRepresentation,
        claimNames: Collection<String>?,
    ): KmmResult<CredentialToBeIssued> = catching {
        val issuance = Clock.System.now().toJavaInstant().truncatedTo(ChronoUnit.SECONDS).toKotlinInstant()
        val expiration = (issuance + lifetime).toJavaInstant().truncatedTo(ChronoUnit.SECONDS).toKotlinInstant()
        Napier.v("getCredential for $credentialScheme and ${subjectPublicKey.didEncoded} in $representation, $claimNames")
        Napier.v("getCredential user is $userInfo")
        createCredential(
            representation,
            credentialScheme,
            userInfo,
            subjectPublicKey,
            expiration,
            issuance,
            claimNames,
            ePrescriptionLoader
        )
    }
}


private fun createCredential(
    representation: ConstantIndex.CredentialRepresentation,
    credentialScheme: ConstantIndex.CredentialScheme,
    oidcUserInfo: OidcUserInfoExtended,
    subjectPublicKey: CryptoPublicKey,
    expiration: Instant,
    issuance: Instant,
    claimNames: Collection<String>?,
    ePrescriptionLoader: EPrescriptionLoader,
): CredentialToBeIssued {
    if (credentialScheme.supportedRepresentations.contains(representation)) {
        val claimsToBeIssued = credentialScheme.buildClaims(
            representation,
            claimNames,
            oidcUserInfo,
            issuance,
            expiration,
            ePrescriptionLoader
        )

        return when (representation) {
            ConstantIndex.CredentialRepresentation.PLAIN_JWT -> when (credentialScheme) {
                IdAustriaScheme -> oidcUserInfo.toIdaCredential(subjectPublicKey, expiration, credentialScheme)
                EuPidScheme -> oidcUserInfo.toEuPidCredential(subjectPublicKey, issuance, expiration, credentialScheme)
                else -> throw IllegalArgumentException(credentialScheme.schemaUri + representation.name)
            }

            ConstantIndex.CredentialRepresentation.SD_JWT ->
                claimsToBeIssued.toSdJwtClaims(subjectPublicKey, expiration, credentialScheme)

            ConstantIndex.CredentialRepresentation.ISO_MDOC ->
                claimsToBeIssued.toIsoClaims(subjectPublicKey, expiration, credentialScheme)
        }
    } else throw IllegalArgumentException(credentialScheme.schemaUri + representation.name)
}
