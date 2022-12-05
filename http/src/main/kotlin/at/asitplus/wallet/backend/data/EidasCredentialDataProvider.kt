package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.wallet.lib.DataSourceProblem
import at.asitplus.wallet.lib.data.AtomicAttributeCredential
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.SchemaIndex
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * Gets credentials for the currently authenticated user from
 * the previously stored attributes (from an OIDC login),
 * i.e. it looks up data with the `bpk` from its internal map
 */
class EidasCredentialDataProvider(private val timeout: Duration, private val clock: Clock) :
    CredentialDataProvider {


    private val list = mutableListOf<EidasClaimHolder>()

    fun storeClaims(eidasClaim: EidasClaim, bpk: String) {
        list.removeAll { it.expiration < clock.now() }
        list += EidasClaimHolder(
            expiration = clock.now() + timeout,
            bpk = bpk,
            claim = eidasClaim,
        )
    }

    override fun getClaim(
        subjectId: String,
        attributeName: String,
        bpk: String,
        maxExpiration: Instant
    ): KmmResult<CredentialDataProvider.CredentialToBeIssued> {
        if (!attributeName.startsWith(SchemaIndex.ATTR_GENERIC_PREFIX))
            return KmmResult.failure(UnsupportedOperationException("$attributeName is not supported")) // other attribute names are not supported

        val eidasClaim = list.firstOrNull { it.bpk == bpk }?.claim
            ?: return KmmResult.failure(DataSourceProblem("Found no stored EIDAS claim for bpk").also {
                Napier.v("bpk: '$bpk'") // TODO: This is out-of-order, as the NPE is only caught later
            })

        val subject = when (attributeName.removePrefix(SchemaIndex.ATTR_GENERIC_PREFIX + "/")) {
            "given-name" -> AtomicAttributeCredential(
                subjectId,
                attributeName,
                eidasClaim.givenName
            )
            "family-name" -> AtomicAttributeCredential(
                subjectId,
                attributeName,
                eidasClaim.familyName
            )

            "date-of-birth" -> AtomicAttributeCredential(
                subjectId,
                attributeName,
                eidasClaim.birthdate
            )

            "identifier" -> AtomicAttributeCredential(subjectId, attributeName, eidasClaim.subject)
            else -> return KmmResult.failure(DataSourceProblem("Requested attribute '$attributeName' could not be issued"))
        }

        return KmmResult.success(
            CredentialDataProvider.CredentialToBeIssued(
                subject,
                maxExpiration,
                ConstantIndex.Generic.vcType
            )
        )
    }

    override fun getCredential(
        subjectId: String,
        attributeType: String,
        bpk: String,
        maxExpiration: Instant
    ) =
        KmmResult.failure<CredentialDataProvider.CredentialToBeIssued>(UnsupportedOperationException("not supported for EIDAS"))


    data class EidasClaim(
        val subject: String,
        val birthdate: String,
        val givenName: String,
        val familyName: String
    )

    data class EidasClaimHolder(val expiration: Instant, val bpk: String, val claim: EidasClaim)

}