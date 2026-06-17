package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.catching
import at.asitplus.wallet.backend.config.buildEuPidCredential
import at.asitplus.wallet.backend.config.buildIsoClaims
import at.asitplus.wallet.backend.config.buildSdJwtClaims
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.*
import at.asitplus.wallet.lib.data.IsoMdocCredentialScheme
import at.asitplus.wallet.lib.data.SdJwtCredentialScheme
import at.asitplus.wallet.lib.data.VcJwtCredentialScheme
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

        when (val scheme = input.credentialScheme) {
            is VcJwtCredentialScheme if input.credentialRepresentation == PLAIN_JWT ->
                when (scheme.vcType) {
                    "EuPid2023" -> input.userInfo.buildEuPidCredential(input.subjectPublicKey, expiration, scheme)
                    else -> throw IllegalArgumentException("$scheme not supporting ${input.credentialRepresentation}")
                }

            is SdJwtCredentialScheme if input.credentialRepresentation == SD_JWT ->
                scheme.buildSdJwtClaims(input.userInfo, issuance, expiration, input.subjectPublicKey)

            is IsoMdocCredentialScheme if input.credentialRepresentation == ISO_MDOC ->
                scheme.buildIsoClaims(input.userInfo, expiration, input.subjectPublicKey)

            else -> throw IllegalArgumentException("No data for $scheme")
        }
    }

}


