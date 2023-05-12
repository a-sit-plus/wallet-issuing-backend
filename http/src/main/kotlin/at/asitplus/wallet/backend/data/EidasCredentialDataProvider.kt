package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.lib.DataSourceProblem
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import kotlin.time.Duration

/**
 * Gets credentials for the currently authenticated user from
 * the previously stored attributes (from an OIDC login),
 * i.e. it looks up data with the `bpk` from its internal map
 */
class EidasCredentialDataProvider(
    private val timeout: Duration,
    private val authenticationSupplier: AuthenticationSupplier,
) : CredentialDataProvider {

    private val list = mutableListOf<EidasClaimHolder>()

    fun storeClaims(eidasClaim: EidasClaim, bpk: String) {
        list.removeAll { it.expiration < Clock.System.now() }
        list += EidasClaimHolder(expiration = Clock.System.now() + timeout, bpk = bpk, claim = eidasClaim)
    }

    override fun getCredentialWithType(
        subjectId: String,
        attributeTypes: Collection<String>,
        bpk: String?,
        maxExpiration: Instant
    ): KmmResult<List<CredentialDataProvider.CredentialToBeIssued>> {
        Napier.v("getCredentialWithType for $subjectId and $attributeTypes and $bpk")
        if (attributeTypes.contains("IdAustriaCredential")) {
            val idToken = authenticationSupplier.getCurrentUserOidcDetails()
            Napier.v("getCredentialWithType user is $idToken")
            if (idToken != null) {
                return issueFromAppOidc(subjectId, idToken, maxExpiration)
            }
            if (bpk != null) {
                return issueFromWebOidc(subjectId, bpk, maxExpiration)
            }
            return KmmResult.success(listOf())
        }
        return KmmResult.success(listOf())
    }

    private fun issueFromWebOidc(
        subjectId: String,
        bpk: String,
        maxExpiration: Instant
    ): KmmResult<List<CredentialDataProvider.CredentialToBeIssued>> {

        val eidasClaim = list.firstOrNull { it.bpk == bpk }?.claim
            ?: return KmmResult.failure(DataSourceProblem("Found no stored EIDAS claim for bpk").also {
                Napier.v("Found no stored EIDAS claim for bpk: '$bpk'")
            })

        val subject = IdAustriaCredential(
            id = subjectId,
            firstname = eidasClaim.givenName,
            lastname = eidasClaim.familyName,
            dateOfBirth = LocalDate.parse(eidasClaim.birthdate)
        )
        Napier.v("getCredentialWithType issuing $subject")
        return KmmResult.success(
            listOf(
                CredentialDataProvider.CredentialToBeIssued(
                    subject = subject,
                    expiration = maxExpiration,
                    attributeType = at.asitplus.wallet.idaustria.ConstantIndex.IdAustriaCredential.vcType
                )
            )
        )
    }

    private fun issueFromAppOidc(
        subjectId: String,
        idToken: OidcIdToken,
        maxExpiration: Instant
    ): KmmResult.Success<List<CredentialDataProvider.CredentialToBeIssued>> {
        val subject = IdAustriaCredential(
            id = subjectId,
            firstname = idToken.givenName,
            lastname = idToken.familyName,
            dateOfBirth = LocalDate.parse(idToken.birthdate)
        )
        Napier.v("getCredentialWithType issuing $subject")
        return KmmResult.success(
            listOf(
                CredentialDataProvider.CredentialToBeIssued(
                    subject = subject,
                    expiration = maxExpiration,
                    attributeType = at.asitplus.wallet.idaustria.ConstantIndex.IdAustriaCredential.vcType
                )
            )
        )
    }

    data class EidasClaim(
        val subject: String,
        val birthdate: String,
        val givenName: String,
        val familyName: String
    )

    data class EidasClaimHolder(val expiration: Instant, val bpk: String, val claim: EidasClaim)

}
