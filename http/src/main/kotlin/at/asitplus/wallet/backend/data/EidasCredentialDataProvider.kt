package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.idaustria.ConstantIndex
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.lib.DataSourceProblem
import at.asitplus.wallet.lib.cbor.CoseKey
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.iso.DrivingPrivilege
import at.asitplus.wallet.lib.iso.ElementValue
import at.asitplus.wallet.lib.iso.IsoDataModelConstants
import at.asitplus.wallet.lib.iso.IssuerSignedItem
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import kotlin.random.Random
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
        maxExpiration: Instant,
        subjectPublicKey: CoseKey?,
    ): KmmResult<List<CredentialDataProvider.CredentialToBeIssued>> {
        Napier.v("getCredentialWithType for $subjectId and $attributeTypes and $bpk")
            val idToken = authenticationSupplier.getCurrentUserOidcDetails()
        if (attributeTypes.contains(ConstantIndex.IdAustriaCredential.vcType)) {
            Napier.v("getCredentialWithType user is $idToken")
            if (idToken != null) {
                return issueFromAppOidc(subjectId, idToken, maxExpiration)
            }
            if (bpk != null) {
                return issueFromWebOidc(subjectId, bpk, maxExpiration)
            }
            return KmmResult.success(listOf())
        }
        if (attributeTypes.contains(at.asitplus.wallet.lib.data.ConstantIndex.MobileDrivingLicence2023.vcType) && subjectPublicKey != null) {
            val drivingPrivilege = DrivingPrivilege(
                vehicleCategoryCode = "B",
                issueDate = LocalDate.parse("2023-01-01"),
                expiryDate = LocalDate.parse("2033-01-31"),
                //codes = arrayOf(DrivingPrivilegeCode(code = "B"))
            )
            val issuerSignedItems = listOf(
                buildIssuerSignedItem(IsoDataModelConstants.DataElements.FAMILY_NAME, idToken?.familyName ?: "Mustermann", 0U),
                buildIssuerSignedItem(IsoDataModelConstants.DataElements.GIVEN_NAME, idToken?.givenName ?: "Max", 1U),
                buildIssuerSignedItem(IsoDataModelConstants.DataElements.DOCUMENT_NUMBER, "123456789", 2U),
                buildIssuerSignedItem(IsoDataModelConstants.DataElements.ISSUE_DATE, "2023-01-01", 3U),
                buildIssuerSignedItem(IsoDataModelConstants.DataElements.EXPIRY_DATE, "2033-01-31", 4U),
                buildIssuerSignedItem(IsoDataModelConstants.DataElements.DRIVING_PRIVILEGES, drivingPrivilege, 5U),
            )

            return KmmResult.success(listOf(
                CredentialDataProvider.CredentialToBeIssued.Iso(
                    issuerSignedItems = issuerSignedItems,
                    subjectPublicKey = subjectPublicKey,
                    expiration = maxExpiration,
                    attributeType = at.asitplus.wallet.lib.data.ConstantIndex.MobileDrivingLicence2023.vcType,
                )
            ))
        }
        return KmmResult.success(listOf())
    }

    fun buildIssuerSignedItem(elementIdentifier: String, elementValue: String, digestId: UInt) = IssuerSignedItem(
        digestId = digestId,
        random = Random.nextBytes(16),
        elementIdentifier = elementIdentifier,
        elementValue = ElementValue(string = elementValue)
    )

    fun buildIssuerSignedItem(elementIdentifier: String, elementValue: DrivingPrivilege, digestId: UInt) =
        IssuerSignedItem(
            digestId = digestId,
            random = Random.nextBytes(16),
            elementIdentifier = elementIdentifier,
            elementValue = ElementValue(drivingPrivilege = arrayOf(elementValue))
        )

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
                CredentialDataProvider.CredentialToBeIssued.Vc(
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
    ): KmmResult<List<CredentialDataProvider.CredentialToBeIssued>> {
        val subject = IdAustriaCredential(
            id = subjectId,
            firstname = idToken.givenName,
            lastname = idToken.familyName,
            dateOfBirth = LocalDate.parse(idToken.birthdate)
        )
        Napier.v("getCredentialWithType issuing $subject")
        return KmmResult.success(
            listOf(
                CredentialDataProvider.CredentialToBeIssued.Vc(
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
