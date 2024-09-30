package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.catching
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.backend.config.*
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.ConstantIndex
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import kotlin.time.Duration


/**
 * Implements interface from VC Library to extract data from [OidcIdToken] and issue the credentials.
 */
class IssuerCredentialDataProviderAdapter(
    private val lifetime: Duration,
    private val authenticationSupplier: AuthenticationSupplier,
    private val ePrescriptionLoader: EPrescriptionLoader,
) : IssuerCredentialDataProvider {

    override fun getCredential(
        subjectPublicKey: CryptoPublicKey,
        credentialScheme: ConstantIndex.CredentialScheme,
        representation: ConstantIndex.CredentialRepresentation,
        claimNames: Collection<String>?,
    ): KmmResult<CredentialToBeIssued> = catching {
        val issuance = Clock.System.now()
        val expiration = issuance + lifetime
        Napier.v("getCredential for $credentialScheme and $subjectPublicKey in $representation")
        val userInfo = authenticationSupplier.getCurrentUserOidcDetails()
        Napier.v("getCredential user is $userInfo")
        if (userInfo == null) {
            throw IllegalArgumentException("idToken")
        }
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

class OidcIssuerCredentialDataProvider(
    private val userInfo: OidcUserInfoExtended,
    private val lifetime: Duration,
    private val ePrescriptionLoader: EPrescriptionLoader,
) : IssuerCredentialDataProvider {

    override fun getCredential(
        subjectPublicKey: CryptoPublicKey,
        credentialScheme: ConstantIndex.CredentialScheme,
        representation: ConstantIndex.CredentialRepresentation,
        claimNames: Collection<String>?,
    ): KmmResult<CredentialToBeIssued> = catching {
        val issuance = Clock.System.now()
        val expiration = issuance + lifetime
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
    ePrescriptionLoader: EPrescriptionLoader
): CredentialToBeIssued {
    if (credentialScheme.supportedRepresentations.contains(representation)) {
        val claimsToBeIssued =
            credentialScheme.buildClaims(claimNames, oidcUserInfo, issuance, expiration, ePrescriptionLoader)

        return when (representation) {
            ConstantIndex.CredentialRepresentation.PLAIN_JWT -> when (credentialScheme) {
                IdAustriaScheme -> oidcUserInfo.toIdaCredential(subjectPublicKey, expiration)
                EuPidScheme -> oidcUserInfo.toEuPidCredential(subjectPublicKey, issuance, expiration)
                else -> throw IllegalArgumentException(credentialScheme.schemaUri + representation.name)
            }

            ConstantIndex.CredentialRepresentation.SD_JWT -> claimsToBeIssued.toSdJwtClaims(expiration)

            ConstantIndex.CredentialRepresentation.ISO_MDOC -> claimsToBeIssued.toIsoClaims(expiration)
        }
    } else throw IllegalArgumentException(credentialScheme.schemaUri + representation.name)
}
