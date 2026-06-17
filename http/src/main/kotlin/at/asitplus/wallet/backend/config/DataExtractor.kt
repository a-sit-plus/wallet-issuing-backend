package at.asitplus.wallet.backend.config

import at.asitplus.iso.IssuerSignedItem
import at.asitplus.openid.OidcAddressClaim
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.io.Base64Strict
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.ageverification.AgeVerificationScheme
import at.asitplus.wallet.cor.CertificateOfResidenceDataElements
import at.asitplus.wallet.ehic.EhicScheme
import at.asitplus.wallet.eupid.EU_PID_DOCTYPE
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidDataElements
import at.asitplus.wallet.eupid.PlaceOfBirth
import at.asitplus.wallet.eupidsdjwt.EU_PID_SD_JWT_VCT
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtDataElements
import at.asitplus.wallet.lib.agent.ClaimToBeIssued
import at.asitplus.wallet.lib.agent.ClaimToBeIssuedArrayElement
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.data.CredentialScheme
import at.asitplus.wallet.lib.data.IsoMdocCredentialScheme
import at.asitplus.wallet.lib.data.LocalDateOrInstant
import at.asitplus.wallet.lib.data.SdJwtCredentialScheme
import at.asitplus.wallet.lib.data.VcJwtCredentialScheme
import at.asitplus.wallet.lib.jws.JwsHeaderModifierFun
import at.asitplus.wallet.mdl.MDL_DOCTYPE
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import at.asitplus.wallet.por.PowerOfRepresentationDataElements
import at.asitplus.wallet.taxid.TaxIdScheme
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.random.Random
import kotlin.time.Instant

fun SdJwtCredentialScheme.buildSdJwtClaims(
    userInfo: OidcUserInfoExtended,
    iss: Instant,
    exp: Instant,
    subjectPublicKey: CryptoPublicKey,
) = CredentialToBeIssued.VcSd(
    claims = when (sdJwtType) {
        EU_PID_SD_JWT_VCT -> userInfo.buildEupidClaimsSdJwt(true)
        "urn:eu.europa.ec.eudi:tax:1" -> userInfo.buildTaxIdClaims(iss, exp, false)
        "urn:eu.europa.ec.eudi:por:1" -> userInfo.buildPorClaims(iss, exp, false)
        "eu.europa.ec.eudi.cor.1" -> userInfo.buildCorClaims(iss, exp, true)
        "urn:eudi:ehic:1" -> userInfo.buildEhicClaims(iss, exp, false)
        else -> TODO("$this is not implemented in buildSdJwtClaims()")
    },
    expiration = exp,
    scheme = this,
    subjectPublicKey = subjectPublicKey,
    userInfo = userInfo,
    modifyHeader = appendEhicVctm()
).also { Napier.v("${this}.buildSdJwtClaims returns $it") }

private fun CredentialScheme.appendEhicVctm(): JwsHeaderModifierFun = {
    if (this is EhicScheme)
        it.copy(
            vcTypeMetadata = setOf(EHIC_VCTM.trimIndent().replace("\n", ""))
        )
    else
        it
}

fun IsoMdocCredentialScheme.buildIsoClaims(
    userInfo: OidcUserInfoExtended,
    exp: Instant,
    subjectPublicKey: CryptoPublicKey,
) = CredentialToBeIssued.Iso(
    issuerSignedItems = when (this.isoDocType) {
        EU_PID_DOCTYPE -> userInfo.buildEupidClaims(true)
        MDL_DOCTYPE -> userInfo.buildMdlClaims(true)
        "eu.europa.ec.av.1" -> userInfo.buildAgeClaims(true)
        else -> TODO("$this is not implemented in buildIsoClaims()")
    }.mapIndexed { idx, it -> it.buildIssuerSignedItem(idx) },
    expiration = exp,
    scheme = this,
    subjectPublicKey = subjectPublicKey,
    userInfo = userInfo,
).also { Napier.v("${this}.buildIsoClaims returns $it") }

fun ClaimToBeIssued.buildIssuerSignedItem(index: Int) = IssuerSignedItem(
    digestId = index.toUInt(),
    random = Random.nextBytes(16),
    elementIdentifier = name,
    elementValue = value
)

fun OidcUserInfoExtended.buildEuPidCredential(
    pubKey: CryptoPublicKey,
    exp: Instant,
    scheme: VcJwtCredentialScheme,
) = CredentialToBeIssued.VcJwt(
    subject = EuPidCredential(
        id = pubKey.didEncoded,
        familyName = userInfo.familyName ?: "N/A",
        givenName = userInfo.givenName ?: "N/A",
        birthDate = dateOfBirth,
        portrait = portrait,
        issuanceDate = LocalDateOrInstant.LocalDate(issueDate),
        expiryDate = LocalDateOrInstant.LocalDate(expiryDate),
        issuingAuthority = issuingAuthority,
        issuingCountry = issuingCountry,
    ).let {
        joseCompliantSerializer.encodeToJsonElement(it)
    }.also { Napier.v("toEuPidCredential returns $it") },
    expiration = exp,
    scheme = scheme,
    subjectPublicKey = pubKey,
    userInfo = this,
)


fun OidcUserInfoExtended.buildEupidClaimsSdJwt(useSd: Boolean) =
    with(EuPidSdJwtDataElements) {
        val address = addressOrRandom
        val birthAddress = randomAddress
        listOfNotNull(
            claim(FAMILY_NAME, useSd) { userInfo.familyName },
            claim(GIVEN_NAME, useSd) { userInfo.givenName },
            claim(BIRTH_DATE, useSd) { dateOfBirth },
            claim(PORTRAIT, useSd) { portrait?.let { "data:image/jpeg;base64,${it.encodeToString(Base64Strict)}" } },
            claim(FAMILY_NAME_BIRTH, useSd) { userInfo.familyName },
            claim(GIVEN_NAME_BIRTH, useSd) { userInfo.givenName },
            claim(PREFIX_PLACE_OF_BIRTH, useSd) {
                with(EuPidSdJwtDataElements.PlaceOfBirth) {
                    listOf(
                        claim(LOCALITY, useSd) { birthAddress.city },
                        claim(COUNTRY, useSd) { birthAddress.country },
                        claim(REGION, useSd) { birthAddress.state },
                    )
                }
            },
            claim(PREFIX_ADDRESS, useSd) {
                with(EuPidSdJwtDataElements.Address) {
                    listOf(
                        claim(FORMATTED, useSd) { address.formatted },
                        claim(COUNTRY, useSd) { address.country },
                        claim(REGION, useSd) { address.state },
                        claim(LOCALITY, useSd) { address.city },
                        claim(POSTAL_CODE, useSd) { address.postCode },
                        claim(STREET, useSd) { address.street },
                        claim(HOUSE_NUMBER, useSd) { address.locator },
                    )
                }
            },
            claim(SEX, useSd) { gender },
            claim(NATIONALITIES, useSd) { setOf(ClaimToBeIssuedArrayElement(nationality)) },
            claim(ISSUANCE_DATE, useSd) { LocalDateOrInstant.LocalDate(issueDate) },
            claim(EXPIRY_DATE, useSd) { LocalDateOrInstant.LocalDate(expiryDate) },
            claim(ISSUING_AUTHORITY, useSd) { issuingAuthority },
            claim(DOCUMENT_NUMBER, useSd) { randomIdentifier },
            claim(ISSUING_COUNTRY, useSd) { issuingCountry },
            claim(ISSUING_JURISDICTION, useSd) { issuingJurisdiction },
            claim(PERSONAL_ADMINISTRATIVE_NUMBER, useSd) { randomIdentifier },
            claim(EMAIL, useSd) { email },
            claim(PHONE_NUMBER, useSd) { phoneNumber },
            claim(TRUST_ANCHOR, useSd) { trustAnchor },
        )
    }

fun OidcUserInfoExtended.buildEupidClaims(useSd: Boolean) =
    with(EuPidDataElements) {
        val address = addressOrRandom
        val birthAddress = randomAddress
        listOfNotNull(
            claim(FAMILY_NAME, useSd) { userInfo.familyName },
            claim(GIVEN_NAME, useSd) { userInfo.givenName },
            claim(BIRTH_DATE, useSd) { dateOfBirth },
            claim(PLACE_OF_BIRTH, useSd) {
                PlaceOfBirth(
                    country = birthAddress.country,
                    region = birthAddress.state,
                    locality = birthAddress.city
                )
            },
            claim(NATIONALITY, useSd) { setOf(nationality) },
            claim(RESIDENT_ADDRESS, useSd) { address.formatted },
            claim(RESIDENT_COUNTRY, useSd) { address.country },
            claim(RESIDENT_STATE, useSd) { address.state },
            claim(RESIDENT_CITY, useSd) { address.city },
            claim(RESIDENT_POSTAL_CODE, useSd) { address.postCode },
            claim(RESIDENT_STREET, useSd) { address.street },
            claim(RESIDENT_HOUSE_NUMBER, useSd) { address.locator.toString() },
            claim(PERSONAL_ADMINISTRATIVE_NUMBER, useSd) { randomIdentifier },
            claim(PORTRAIT, useSd) { portrait },
            claim(FAMILY_NAME_BIRTH, useSd) { userInfo.familyName },
            claim(GIVEN_NAME_BIRTH, useSd) { userInfo.givenName },
            claim(SEX, useSd) { gender.code },
            claim(EMAIL_ADDRESS, useSd) { email },
            claim(MOBILE_PHONE_NUMBER, useSd) { phoneNumber },
            claim(EXPIRY_DATE, useSd) { LocalDateOrInstant.LocalDate(expiryDate) },
            claim(ISSUING_AUTHORITY, useSd) { issuingAuthority },
            claim(ISSUING_COUNTRY, useSd) { issuingCountry },
            claim(DOCUMENT_NUMBER, useSd) { randomIdentifier },
            claim(ISSUING_JURISDICTION, useSd) { issuingJurisdiction },
            claim(ISSUANCE_DATE, useSd) { LocalDateOrInstant.LocalDate(issueDate) },
            claim(TRUST_ANCHOR, useSd) { trustAnchor },
            claim(LOCATION_STATUS, useSd) { trustAnchor },
        )
    }

private val OidcUserInfoExtended.addressOrRandom: Address
    get() = userInfo.address?.parseOidcAddress()
        ?: parseIdAustriaAddress()
        ?: randomAddress


private fun OidcAddressClaim.parseOidcAddress(): Address? =
    if (postalCode != null && locality != null && region != null && street != null) {
        Address(
            postCode = postalCode!!,
            city = locality!!,
            state = region!!,
            street = street!!.substringBefore(" "),
            locator = street!!.substringAfter(" ").toIntOrNull() ?: randomAddressLocator,
            country = country ?: "AT",
            formattedInt = formatted
        )
    } else null

fun OidcUserInfoExtended.buildPorClaims(iss: Instant, exp: Instant, useSd: Boolean) =
    with(PowerOfRepresentationDataElements) {
        listOfNotNull(
            claim(LEGAL_PERSON_IDENTIFIER, useSd) { legalPersonIdentifier },
            claim(LEGAL_NAME, useSd) { legalName },
            claim(FULL_POWERS, useSd) { true },
            //claim(E_SERVICE, useSd) { eService },
            claim(EFFECTIVE_FROM_DATE, useSd) { iss },
            claim(EFFECTIVE_UNTIL_DATE, useSd) { exp },
            claim(ISSUANCE_DATE, useSd) { iss },
            claim(EXPIRY_DATE, useSd) { exp },
            claim(ISSUING_AUTHORITY, useSd) { issuingAuthority },
            claim(ISSUING_COUNTRY, useSd) { issuingCountry },
            claim(ISSUING_JURISDICTION, useSd) { issuingJurisdiction },
            claim(DOCUMENT_NUMBER, useSd) { randomIdentifier },
            claim(ADMINISTRATIVE_NUMBER, useSd) { randomIdentifier },
        )
    }

fun OidcUserInfoExtended.buildTaxIdClaims(iss: Instant, exp: Instant, useSd: Boolean) =
    with(TaxIdScheme.Attributes) {
        listOfNotNull(
            claim(TAX_NUMBER, useSd) { randomTaxNumber },
            claim(AFFILIATION_COUNTRY, useSd) { fallbackAddressCountry },
            claim(REGISTERED_GIVEN_NAME, useSd) { userInfo.givenName },
            claim(REGISTERED_FAMILY_NAME, useSd) { userInfo.familyName },
            claim(RESIDENT_ADDRESS, useSd) {
                addressOrRandom.formatted
            },
            claim(BIRTH_DATE, useSd) { dateOfBirth },
            claim(CHURCH_TAX_ID, useSd) { randomChurchTaxId },
            claim(IBAN, useSd) { fallbackIban },
            claim(PID_ID, useSd) { randomPidId },
            claim(ISSUANCE_DATE, useSd) { iss },
            claim(EXPIRY_DATE, useSd) { exp },
            claim(ISSUING_AUTHORITY, useSd) { issuingAuthority },
            claim(ISSUING_COUNTRY, useSd) { issuingCountry },
            claim(ISSUING_JURISDICTION, useSd) { issuingJurisdiction },
            claim(DOCUMENT_NUMBER, useSd) { randomIdentifier },
            claim(ADMINISTRATIVE_NUMBER, useSd) { randomIdentifier },
        )
    }

fun OidcUserInfoExtended.buildEhicClaims(iss: Instant, exp: Instant, useSd: Boolean): List<ClaimToBeIssued> =
    with(EhicScheme.Attributes) {
        val issuingAuthorityId = randomIdentifier
        val authenticSourceId = randomIdentifier
        listOfNotNull(
            claim(ISSUING_COUNTRY, useSd) { issuingCountry },
            claim(PERSONAL_ADMINISTRATIVE_NUMBER, useSd) { socialSecurityNumber },
            claim(DOCUMENT_NUMBER, useSd) { randomIdentifier },
            claim(ISSUING_AUTHORITY_ID, useSd) { issuingAuthorityId },
            claim(ISSUING_AUTHORITY_NAME, useSd) { issuingAuthority },
            claim(PREFIX_ISSUING_AUTHORITY, useSd) {
                with(EhicScheme.Attributes.IssuingAuthority) {
                    listOf(
                        claim(ID, useSd) { issuingAuthorityId },
                        claim(NAME, useSd) { issuingAuthority }
                    )
                }
            },
            claim(AUTHENTIC_SOURCE_ID, useSd) { authenticSourceId },
            claim(AUTHENTIC_SOURCE_NAME, useSd) { authenticSource },
            claim(PREFIX_AUTHENTIC_SOURCE, useSd) {
                with(EhicScheme.Attributes.AuthenticSource) {
                    listOf(
                        claim(ID, useSd) { authenticSourceId },
                        claim(NAME, useSd) { authenticSource }
                    )
                }
            },
            claim(DATE_OF_ISSUANCE, useSd) { iss.toLocalDate() },
            claim(DATE_OF_EXPIRY, useSd) { exp.toLocalDate() },
            claim(STARTING_DATE, useSd) { expiryDate },
            claim(ENDING_DATE, useSd) { expiryDate },
        )
    }

fun OidcUserInfoExtended.buildCorClaims(iss: Instant, exp: Instant, useSd: Boolean) =
    with(CertificateOfResidenceDataElements) {
        listOfNotNull(
            claim(FAMILY_NAME, useSd) { userInfo.familyName },
            claim(GIVEN_NAME, useSd) { userInfo.givenName },
            claim(BIRTH_DATE, useSd) { dateOfBirth },
            claim(RESIDENCE_ADDRESS, useSd) {
                with(CertificateOfResidenceDataElements.Address) {
                    with(addressOrRandom) {
                        listOf(
                            claim(THOROUGHFARE, useSd) { street },
                            claim(LOCATOR_DESIGNATOR, useSd) { locator },
                            claim(POST_CODE, useSd) { postCode },
                            claim(POST_NAME, useSd) { city },
                            claim(ADMIN_UNIT_L_1, useSd) { country },
                            claim(ADMIN_UNIT_L_2, useSd) { state },
                            claim(FULL_ADDRESS, useSd) { formatted },
                        )
                    }
                }
            },
            claim(GENDER, useSd) { gender },
            claim(BIRTH_PLACE, useSd) { randomAddress.city },
            claim(ARRIVAL_DATE, useSd) { arrivalDate },
            claim(NATIONALITY, useSd) { nationality },
            claim(ISSUANCE_DATE, useSd) { iss },
            claim(EXPIRY_DATE, useSd) { exp },
            claim(ISSUING_AUTHORITY, useSd) { issuingAuthority },
            claim(DOCUMENT_NUMBER, useSd) { randomIdentifier },
            claim(ADMINISTRATIVE_NUMBER, useSd) { randomIdentifier },
            claim(ISSUING_COUNTRY, useSd) { issuingCountry },
            claim(ISSUING_JURISDICTION, useSd) { issuingJurisdiction },
        )
    }

fun OidcUserInfoExtended.buildMdlClaims(useSd: Boolean) =
    with(MobileDrivingLicenceDataElements) {
        val address = addressOrRandom
        listOfNotNull(
            claim(FAMILY_NAME, useSd) { userInfo.familyName },
            claim(GIVEN_NAME, useSd) { userInfo.givenName },
            claim(BIRTH_DATE, useSd) { dateOfBirth },
            claim(ISSUE_DATE, useSd) { issueDate },
            claim(EXPIRY_DATE, useSd) { expiryDate },
            claim(ISSUING_COUNTRY, useSd) { issuingCountry },
            claim(ISSUING_AUTHORITY, useSd) { issuingAuthority },
            claim(DOCUMENT_NUMBER, useSd) { randomIdentifier },
            claim(PORTRAIT, useSd) { portrait },
            claim(DRIVING_PRIVILEGES, useSd) { arrayOf(fakeDrivingPrivilege) },
            claim(UN_DISTINGUISHING_SIGN, useSd) { unDistinguishingSign },
            claim(ADMINISTRATIVE_NUMBER, useSd) { randomIdentifier },
            claim(SEX, useSd) { sex },
            claim(HEIGHT, useSd) { randomHeight },
            claim(WEIGHT, useSd) { randomWeight },
            claim(EYE_COLOUR, useSd) { randomEyeColour },
            claim(HAIR_COLOUR, useSd) { randomHairColour },
            claim(BIRTH_PLACE, useSd) { randomAddress.city },
            claim(RESIDENT_ADDRESS, useSd) { address.formatted },
            claim(PORTRAIT_CAPTURE_DATE, useSd) { portraitCaptureDate },
            claim(AGE_IN_YEARS, useSd) { ageInYears },
            claim(AGE_BIRTH_YEAR, useSd) { dateOfBirth.year.toUInt() },
            claim(AGE_OVER_12, useSd) { ageOver12 },
            claim(AGE_OVER_13, useSd) { ageOver13 },
            claim(AGE_OVER_14, useSd) { ageOver14 },
            claim(AGE_OVER_16, useSd) { ageOver16 },
            claim(AGE_OVER_18, useSd) { ageOver18 },
            claim(AGE_OVER_21, useSd) { ageOver21 },
            claim(AGE_OVER_25, useSd) { ageOver25 },
            claim(AGE_OVER_60, useSd) { ageOver60 },
            claim(AGE_OVER_62, useSd) { ageOver62 },
            claim(AGE_OVER_65, useSd) { ageOver65 },
            claim(AGE_OVER_68, useSd) { ageOver68 },
            // Would need matching field in certificate claim(ISSUING_JURISDICTION, useSd) { issuingJurisdiction },
            claim(NATIONALITY, useSd) { nationality },
            claim(RESIDENT_CITY, useSd) { address.city },
            claim(RESIDENT_STATE, useSd) { address.state },
            claim(RESIDENT_POSTAL_CODE, useSd) { address.postCode },
            claim(RESIDENT_COUNTRY, useSd) { address.country },
            claim(FAMILY_NAME_NATIONAL_CHARACTER, useSd) { userInfo.familyName + " \uD83E\uDD84" },
            claim(GIVEN_NAME_NATIONAL_CHARACTER, useSd) { userInfo.givenName + " \uD83E\uDD84" },
            claim(SIGNATURE_USUAL_MARK, useSd) { pictureTripleX },
            claim(BIOMETRIC_TEMPLATE_FACE, useSd) { pictureTripleX },
            claim(BIOMETRIC_TEMPLATE_FINGER, useSd) { pictureTripleX },
            claim(BIOMETRIC_TEMPLATE_SIGNATURE_SIGN, useSd) { pictureTripleX },
            claim(BIOMETRIC_TEMPLATE_IRIS, useSd) { pictureTripleX },
        )
    }

fun OidcUserInfoExtended.buildAgeClaims(useSd: Boolean) =
    with(AgeVerificationScheme.Attributes) {
        listOfNotNull(
            claim(AGE_OVER_12, useSd) { ageOver12 },
            claim(AGE_OVER_13, useSd) { ageOver13 },
            claim(AGE_OVER_14, useSd) { ageOver14 },
            claim(AGE_OVER_16, useSd) { ageOver16 },
            claim(AGE_OVER_18, useSd) { ageOver18 },
            claim(AGE_OVER_21, useSd) { ageOver21 },
            claim(AGE_OVER_25, useSd) { ageOver25 },
            claim(AGE_OVER_60, useSd) { ageOver60 },
            claim(AGE_OVER_62, useSd) { ageOver62 },
            claim(AGE_OVER_65, useSd) { ageOver65 },
            claim(AGE_OVER_68, useSd) { ageOver68 },
        )
    }

private const val EHIC_VCTM = """
    eyJ2Y3QiOiJ1cm46ZXVkaTplaGljOjEiLCJuYW1lIjoiRUhJQyBTRC1KV1QgVFlQRSBNRVRBREFUQSIs
    ImRlc2NyaXB0aW9uIjoiRXVyb3BlYW4gSGVhbHRoIEluc3VyYW5jZSBDYXJkIChFSElDKSBTRC1KV1Qg
    VmVyaWZpYWJsZSBDcmVkZW50aWFsIFR5cGUgTWV0YWRhdGEsIGJhc2VkIG9uIGlldGYtb2F1dGgtc2Qt
    and0LXZjIChkcmFmdCAwOSksIHVzaW5nIGEgc2luZ2xlIGxhbmd1YWdlIHRhZyAoZW4tVVMpLiIsIiRj
    b21tZW50IjoiSW1wbGVtZW50YXRpb24gb2YgdGhpcyBleGFtcGxlIFR5cGUgTWV0YWRhdGEgbWF5IHJl
    cXVpcmUgTWVtYmVyIFN0YXRlLXNwZWNpZmljIGNsYXJpZmljYXRpb25zIHRvIGFsaWduIHdpdGggbmF0
    aW9uYWwgcG9saWNpZXMgZ292ZXJuaW5nIHRoZSBkaXNwbGF5IG9mIGluY2x1ZGVkIGNsYWltcy4iLCJk
    aXNwbGF5IjpbeyJsYW5nIjoiZW4tVVMiLCJuYW1lIjoiRUhJQyBTRC1KV1QgVkMiLCJkZXNjcmlwdGlv
    biI6IkV1cm9wZWFuIEhlYWx0aCBJbnN1cmFuY2UgQ2FyZCAoRUhJQykgU0QtSldUIFZDIiwicmVuZGVy
    aW5nIjp7InNpbXBsZSI6eyJiYWNrZ3JvdW5kX2NvbG9yIjoiIzFiMjYzYiIsInRleHRfY29sb3IiOiIj
    RkZGRkZGIn0sInN2Z190ZW1wbGF0ZXMiOlt7InVyaSI6Imh0dHBzOi8vcWEtaXNzdWVyLnd3d2FsbGV0
    Lm9yZy9pbWFnZXMvdGVtcGxhdGUtZWhpYy5zdmciLCJ1cmkjaW50ZWdyaXR5Ijoic2hhMjU2LU5OQ0JF
    Q1ZadzVJeFJLL3ZxLyt4ZjJPY0h2YVJLekNreGhxeGhYalpYa2c9IiwicHJvcGVydGllcyI6eyJvcmll
    bnRhdGlvbiI6ImxhbmRzY2FwZSIsImNvbG9yX3NjaGVtZSI6ImxpZ2h0IiwiY29udHJhc3QiOiJub3Jt
    YWwifX1dfX1dLCJjbGFpbXMiOlt7InBhdGgiOlsianRpIl0sInNkIjoibmV2ZXIifSx7InBhdGgiOlsi
    c3ViIl0sInNkIjoibmV2ZXIifSx7InBhdGgiOlsiaWF0Il0sInNkIjoibmV2ZXIifSx7InBhdGgiOlsi
    cGVyc29uYWxfYWRtaW5pc3RyYXRpdmVfbnVtYmVyIl0sInNkIjoiYWx3YXlzIiwic3ZnX2lkIjoicGVy
    c29uYWxfYWRtaW5pc3RyYXRpdmVfbnVtYmVyIiwiZGlzcGxheSI6W3sibGFuZyI6ImVuLVVTIiwibGFi
    ZWwiOiJTb2NpYWwgU2VjdXJpdHkgUElOIiwiZGVzY3JpcHRpb24iOiJVbmlxdWUgcGVyc29uYWwgaWRl
    bnRpZmllciB1c2VkIGJ5IHNvY2lhbCBzZWN1cml0eSBzZXJ2aWNlcy4ifV19LHsicGF0aCI6WyJpc3N1
    aW5nX2NvdW50cnkiXSwic2QiOiJuZXZlciIsInN2Z19pZCI6Imlzc3Vlcl9jb3VudHJ5IiwiZGlzcGxh
    eSI6W3sibGFuZyI6ImVuLVVTIiwibGFiZWwiOiJJc3N1aW5nIGNvdW50cnkiLCJkZXNjcmlwdGlvbiI6
    IkVISUMgaXNzdWluZyBjb3VudHJ5LiJ9XX0seyJwYXRoIjpbImlzc3VpbmdfYXV0aG9yaXR5Il0sInNk
    IjoibmV2ZXIifSx7InBhdGgiOlsiaXNzdWluZ19hdXRob3JpdHkiLCJpZCJdLCJzZCI6Im5ldmVyIiwi
    ZGlzcGxheSI6W3sibGFuZyI6ImVuLVVTIiwibGFiZWwiOiJJc3N1aW5nIGF1dGhvcml0eSBpZCIsImRl
    c2NyaXB0aW9uIjoiRUhJQyBpc3N1aW5nIGF1dGhvcml0eSB1bmlxdWUgaWRlbnRpZmllci4ifV19LHsi
    cGF0aCI6WyJpc3N1aW5nX2F1dGhvcml0eSIsIm5hbWUiXSwic2QiOiJuZXZlciIsImRpc3BsYXkiOlt7
    ImxhbmciOiJlbi1VUyIsImxhYmVsIjoiSXNzdWluZyBhdXRob3JpdHkgbmFtZSIsImRlc2NyaXB0aW9u
    IjoiRUhJQyBpc3N1aW5nIGF1dGhvcml0eSBuYW1lLiJ9XX0seyJwYXRoIjpbImRhdGVfb2ZfZXhwaXJ5
    Il0sInNkIjoibmV2ZXIiLCJzdmdfaWQiOiJkYXRlX29mX2V4cGlyeSIsImRpc3BsYXkiOlt7Imxhbmci
    OiJlbi1VUyIsImxhYmVsIjoiRXhwaXJ5IGRhdGUiLCJkZXNjcmlwdGlvbiI6IkVISUMgZXhwaXJhdGlv
    biBkYXRlLiJ9XX0seyJwYXRoIjpbImRhdGVfb2ZfaXNzdWFuY2UiXSwic2QiOiJuZXZlciIsImRpc3Bs
    YXkiOlt7ImxhbmciOiJlbi1VUyIsImxhYmVsIjoiSXNzdWUgZGF0ZSIsImRlc2NyaXB0aW9uIjoiRUhJ
    QyB2YWxpZGl0eSBzdGFydCBkYXRlLiJ9XX0seyJwYXRoIjpbImF1dGhlbnRpY19zb3VyY2UiXSwic2Qi
    OiJuZXZlciJ9LHsicGF0aCI6WyJhdXRoZW50aWNfc291cmNlIiwiaWQiXSwic2QiOiJuZXZlciIsInN2
    Z19pZCI6ImF1dGhlbnRpY19zb3VyY2VfaWQiLCJkaXNwbGF5IjpbeyJsYW5nIjoiZW4tVVMiLCJsYWJl
    bCI6IkNvbXBldGVudCBpbnN0aXR1dGlvbiBpZCIsImRlc2NyaXB0aW9uIjoiSWRlbnRpZmllciBvZiB0
    aGUgY29tcGV0ZW50IGluc2l0dXRpb24gYXMgcmVnaXN0ZXJlZCBpbiB0aGUgRUVTU0kgSW5zdGl0dXRp
    b24gUmVwb3NpdG9yeS4ifV19LHsicGF0aCI6WyJhdXRoZW50aWNfc291cmNlIiwibmFtZSJdLCJzZCI6
    Im5ldmVyIiwic3ZnX2lkIjoiYXV0aGVudGljX3NvdXJjZV9uYW1lIiwiZGlzcGxheSI6W3sibGFuZyI6
    ImVuLVVTIiwibGFiZWwiOiJDb21wZXRlbnQgaW5zdGl0dXRpb24gbmFtZSIsImRlc2NyaXB0aW9uIjoi
    TmFtZSBvZiB0aGUgY29tcGV0ZW50IGluc2l0dXRpb24gYXMgcmVnaXN0ZXJlZCBpbiB0aGUgRUVTU0kg
    SW5zdGl0dXRpb24gUmVwb3NpdG9yeS4ifV19LHsicGF0aCI6WyJlbmRpbmdfZGF0ZSJdLCJzZCI6Im5l
    dmVyIiwiZGlzcGxheSI6W3sibGFuZyI6ImVuLVVTIiwibGFiZWwiOiJFbmRpbmcgZGF0ZSIsImRlc2Ny
    aXB0aW9uIjoiRW5kIGRhdGUgb2YgdGhlIGluc3VyYW5jZSBjb3ZlcmFnZS4ifV19LHsicGF0aCI6WyJz
    dGFydGluZ19kYXRlIl0sInNkIjoibmV2ZXIiLCJkaXNwbGF5IjpbeyJsYW5nIjoiZW4tVVMiLCJsYWJl
    bCI6IlN0YXJ0aW5nIGRhdGUiLCJkZXNjcmlwdGlvbiI6IlN0YXJ0IGRhdGUgb2YgdGhlIGluc3VyYW5j
    ZSBjb3ZlcmFnZS4ifV19LHsicGF0aCI6WyJkb2N1bWVudF9udW1iZXIiXSwic2QiOiJhbHdheXMiLCJz
    dmdfaWQiOiJkb2N1bWVudF9udW1iZXIiLCJkaXNwbGF5IjpbeyJsYW5nIjoiZW4tVVMiLCJsYWJlbCI6
    IkRvY3VtZW50IG51bWJlciIsImRlc2NyaXB0aW9uIjoiRUhJQyB1bmlxdWUgZG9jdW1lbnQgaWRlbnRp
    Zmllci4ifV19XSwic2NoZW1hX3VyaSI6Imh0dHBzOi8vcWEtaXNzdWVyLnd3d2FsbGV0Lm9yZy9laGlj
    LXNjaGVtYSIsInNjaGVtYV91cmkjaW50ZWdyaXR5Ijoic2hhMjU2LWNOUzJhalByNnBmWnp0RTBLNVlL
    NGg3RWlTNzNQQ2oxL3YvM1ZmMXpkMUU9In0
"""

private fun claim(key: String, useSd: Boolean, value: () -> Any?): ClaimToBeIssued? =
    value()?.let { ClaimToBeIssued(key, it, useSd) }
