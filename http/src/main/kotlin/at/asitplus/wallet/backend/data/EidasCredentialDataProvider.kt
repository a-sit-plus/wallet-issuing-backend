package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.idaustria.ConstantIndex
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.lib.cbor.CoseKey
import at.asitplus.wallet.lib.data.ConstantIndex.MobileDrivingLicence2023
import at.asitplus.wallet.lib.iso.DrivingPrivilege
import at.asitplus.wallet.lib.iso.DrivingPrivilegeCode
import at.asitplus.wallet.lib.iso.ElementValue
import at.asitplus.wallet.lib.iso.IsoDataModelConstants.DataElements
import at.asitplus.wallet.lib.iso.IssuerSignedItem
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArrayOrNull
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDate
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import kotlin.random.Random

/**
 * Gets credentials for the currently authenticated user from
 * the previously stored attributes (from an OIDC login),
 * i.e. it looks up data with the `bpk` from its internal map
 */
class EidasCredentialDataProvider(
    private val authenticationSupplier: AuthenticationSupplier,
) : CredentialDataProvider {

    override fun getCredentialWithType(
        subjectId: String,
        attributeTypes: Collection<String>,
        maxExpiration: Instant,
        subjectPublicKey: CoseKey?,
    ): KmmResult<List<CredentialDataProvider.CredentialToBeIssued>> {
        Napier.v("getCredentialWithType for $subjectId and $attributeTypes")
        val idToken = authenticationSupplier.getCurrentUserOidcDetails()
        if (attributeTypes.contains(ConstantIndex.IdAustriaCredential.vcType)) {
            Napier.v("getCredentialWithType user is $idToken")
            if (idToken != null) {
                return issueIdAustriaCredential(subjectId, idToken, maxExpiration)
            }
            return KmmResult.success(listOf())
        }
        if (attributeTypes.contains(MobileDrivingLicence2023.vcType) && subjectPublicKey != null) {
            return issueMobileDrivingLicence(idToken, subjectPublicKey, maxExpiration)
        }
        return KmmResult.success(listOf())
    }

    private fun issueMobileDrivingLicence(
        idToken: OidcIdToken?,
        subjectPublicKey: CoseKey,
        maxExpiration: Instant
    ): KmmResult<List<CredentialDataProvider.CredentialToBeIssued>> {
        val drivingPrivilege = DrivingPrivilege(
            vehicleCategoryCode = "B",
            issueDate = LocalDate.parse("2023-01-01"),
            expiryDate = LocalDate.parse("2033-01-31"),
            codes = arrayOf(DrivingPrivilegeCode(code = "B"))
        )
        val issuerSignedItems = listOfNotNull(
            item(0U, DataElements.FAMILY_NAME, ElementValue(string = idToken?.familyName ?: "Mustermann")),
            item(1U, DataElements.GIVEN_NAME, ElementValue(string = idToken?.givenName ?: "Max")),
            item(2U, DataElements.DOCUMENT_NUMBER, ElementValue(string = "123456789")),
            item(3U, DataElements.ISSUE_DATE, ElementValue(string = "2023-01-01")),
            item(4U, DataElements.EXPIRY_DATE, ElementValue(string = "2033-01-31")),
            item(5U, DataElements.DRIVING_PRIVILEGES, ElementValue(drivingPrivilege = arrayOf(drivingPrivilege))),
            idToken?.getClaimAsString("org.iso.18013.5.1:portrait")?.decodeToByteArrayOrNull(Base64())?.let {
                item(6U, DataElements.PORTRAIT, ElementValue(bytes = it))
            },
            idToken?.getClaimAsString("org.iso.18013.5.1:portrait_capture_date")?.toLocalDate()?.let {
                item(7U, DataElements.PORTRAIT_CAPTURE_DATE, ElementValue(date = it))
            },
        )

        return KmmResult.success(
            listOf(
                CredentialDataProvider.CredentialToBeIssued.Iso(
                    issuerSignedItems = issuerSignedItems,
                    subjectPublicKey = subjectPublicKey,
                    expiration = maxExpiration,
                    attributeType = MobileDrivingLicence2023.vcType,
                )
            )
        )
    }

    private fun item(digestId: UInt, elementIdentifier: String, elementValue1: ElementValue) =
        IssuerSignedItem(digestId, Random.nextBytes(16), elementIdentifier, elementValue1)

    private fun issueIdAustriaCredential(
        subjectId: String,
        idToken: OidcIdToken,
        maxExpiration: Instant
    ): KmmResult<List<CredentialDataProvider.CredentialToBeIssued>> {
        val subject = IdAustriaCredential(
            id = subjectId,
            firstname = idToken.givenName,
            lastname = idToken.familyName,
            dateOfBirth = LocalDate.parse(idToken.birthdate),
            portrait = idToken.getClaimAsString("org.iso.18013.5.1:portrait")?.decodeToByteArrayOrNull(Base64())
        )
        Napier.v("getCredentialWithType issuing $subject")
        return KmmResult.success(
            listOf(
                CredentialDataProvider.CredentialToBeIssued.Vc(
                    subject = subject,
                    expiration = maxExpiration,
                    attributeType = ConstantIndex.IdAustriaCredential.vcType
                )
            )
        )
    }
}
