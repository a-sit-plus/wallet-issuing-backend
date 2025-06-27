package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.catching
import at.asitplus.wallet.backend.config.EPrescriptionLoader
import at.asitplus.wallet.backend.config.buildClaims
import at.asitplus.wallet.backend.config.toEuPidCredential
import at.asitplus.wallet.backend.config.toIdaCredential
import at.asitplus.wallet.backend.config.toIsoClaims
import at.asitplus.wallet.backend.config.toSdJwtClaims
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.oidvci.CredentialDataProviderFun
import at.asitplus.wallet.lib.oidvci.CredentialDataProviderInput
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration


class OidcIssuerCredentialDataProvider(
    private val lifetime: Duration,
    private val ePrescriptionLoader: EPrescriptionLoader,
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
                        IdAustriaScheme -> userInfo.toIdaCredential(subjectPublicKey, expiration, credentialScheme)
                        EuPidScheme -> userInfo.toEuPidCredential(
                            subjectPublicKey,
                            issuance,
                            expiration,
                            credentialScheme
                        )

                        else -> throw IllegalArgumentException("$credentialScheme not supporting $credentialRepresentation")
                    }

                    ConstantIndex.CredentialRepresentation.SD_JWT ->
                        credentialScheme.buildClaims(userInfo, issuance, expiration, ePrescriptionLoader)
                            .toSdJwtClaims(subjectPublicKey, expiration, credentialScheme, input.userInfo)

                    ConstantIndex.CredentialRepresentation.ISO_MDOC ->
                        credentialScheme.buildClaims(userInfo, issuance, expiration, ePrescriptionLoader)
                            .toIsoClaims(subjectPublicKey, expiration, credentialScheme, input.userInfo)
                }
            } else throw IllegalArgumentException("$credentialScheme not supporting $credentialRepresentation")
        }
    }
}


