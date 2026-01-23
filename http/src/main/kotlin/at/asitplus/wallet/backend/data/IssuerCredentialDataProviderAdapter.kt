package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.catching
import at.asitplus.wallet.backend.config.buildIsoClaims
import at.asitplus.wallet.backend.config.buildSdJwtClaims
import at.asitplus.wallet.backend.config.toEuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.oidvci.CredentialDataProviderFun
import at.asitplus.wallet.lib.oidvci.CredentialDataProviderInput
import io.github.aakira.napier.Napier
import java.time.temporal.ChronoUnit
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant


class OidcIssuerCredentialDataProvider(
    private val lifetime: Duration,
) : CredentialDataProviderFun {
    override suspend fun invoke(
        input: CredentialDataProviderInput,
    ): KmmResult<CredentialToBeIssued> = catching {
        val issuance = Clock.System.now().toJavaInstant().truncatedTo(ChronoUnit.SECONDS).toKotlinInstant()
        val expiration = (issuance + lifetime).toJavaInstant().truncatedTo(ChronoUnit.SECONDS).toKotlinInstant()
        Napier.v("getCredential for ${input.credentialScheme} and ${input.subjectPublicKey.didEncoded}")
        Napier.v("getCredential user is ${input.userInfo}")
        with(input) {
            if (credentialScheme.supportedRepresentations.contains(credentialRepresentation)) {
                when (credentialRepresentation) {
                    ConstantIndex.CredentialRepresentation.PLAIN_JWT -> when (credentialScheme) {
                        EuPidScheme -> userInfo.toEuPidCredential(subjectPublicKey, expiration, credentialScheme)
                        else -> throw IllegalArgumentException("$credentialScheme not supporting $credentialRepresentation")
                    }

                    ConstantIndex.CredentialRepresentation.SD_JWT ->
                        credentialScheme.buildSdJwtClaims(userInfo, issuance, expiration, subjectPublicKey)

                    ConstantIndex.CredentialRepresentation.ISO_MDOC ->
                        credentialScheme.buildIsoClaims(userInfo, issuance, expiration, subjectPublicKey)
                }
            } else throw IllegalArgumentException("$credentialScheme not supporting $credentialRepresentation")
        }
    }
}


