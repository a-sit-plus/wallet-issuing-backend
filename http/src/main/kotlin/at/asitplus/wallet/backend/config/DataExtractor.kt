package at.asitplus.wallet.backend.config

import at.asitplus.iso.IssuerSignedItem
import at.asitplus.openid.OidcAddressClaim
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.io.Base64Strict
import at.asitplus.wallet.ageverification.AgeVerificationScheme
import at.asitplus.wallet.cor.CertificateOfResidenceDataElements
import at.asitplus.wallet.cor.CertificateOfResidenceScheme
import at.asitplus.wallet.ehic.EhicScheme
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.eupid.PlaceOfBirth
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtScheme
import at.asitplus.wallet.lib.agent.ClaimToBeIssued
import at.asitplus.wallet.lib.agent.ClaimToBeIssuedArrayElement
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.LocalDateOrInstant
import at.asitplus.wallet.lib.jws.JwsHeaderModifierFun
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
import at.asitplus.wallet.por.PowerOfRepresentationDataElements
import at.asitplus.wallet.por.PowerOfRepresentationScheme
import at.asitplus.wallet.taxid.TaxIdScheme
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlin.random.Random
import kotlin.time.Instant

fun ConstantIndex.CredentialScheme.buildSdJwtClaims(
    userInfo: OidcUserInfoExtended,
    iss: Instant,
    exp: Instant,
    subjectPublicKey: CryptoPublicKey,
) = CredentialToBeIssued.VcSd(
    claims = when (this) {
        is EuPidScheme -> userInfo.buildEupidClaims(true)
        is EuPidSdJwtScheme -> userInfo.buildEupidClaimsSdJwt(true)
        is TaxIdScheme -> userInfo.buildTaxIdClaims(iss, exp, false)
        is PowerOfRepresentationScheme -> userInfo.buildPorClaims(iss, exp, false)
        is CertificateOfResidenceScheme -> userInfo.buildCorClaims(iss, exp, true)
        is EhicScheme -> userInfo.buildEhicClaims(iss, exp, false)
        else -> TODO("$this is not implemented in buildSdJwtClaims()")
    },
    expiration = exp,
    scheme = this,
    subjectPublicKey = subjectPublicKey,
    userInfo = userInfo,
    modifyHeader = appendEhicVctm()
).also { Napier.v("${this}.buildSdJwtClaims returns $it") }

private fun ConstantIndex.CredentialScheme.appendEhicVctm(): JwsHeaderModifierFun = {
    if (this is EhicScheme)
        it.copy(
            vcTypeMetadata = setOf(EHIC_VCTM.trimIndent().replace("\n", ""))
        )
    else
        it
}

fun ConstantIndex.CredentialScheme.buildIsoClaims(
    userInfo: OidcUserInfoExtended,
    exp: Instant,
    subjectPublicKey: CryptoPublicKey,
) = CredentialToBeIssued.Iso(
    issuerSignedItems = when (this) {
        is EuPidScheme -> userInfo.buildEupidClaims(true)
        is MobileDrivingLicenceScheme -> userInfo.buildMdlClaims(true)
        is AgeVerificationScheme -> userInfo.buildAgeClaims(true)
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
    scheme: ConstantIndex.CredentialScheme,
) = CredentialToBeIssued.VcJwt(
    subject = EuPidCredential(
        id = pubKey.didEncoded,
        familyName = userInfo.familyName ?: "N/A",
        givenName = userInfo.givenName ?: "N/A",
        birthDate = dateOfBirth,
        portrait = portrait,
        ageOver12 = ageOver12,
        ageOver13 = ageOver13,
        ageOver14 = ageOver14,
        ageOver16 = ageOver16,
        ageOver18 = ageOver18,
        ageOver21 = ageOver21,
        ageOver25 = ageOver25,
        ageOver60 = ageOver60,
        ageOver62 = ageOver62,
        ageOver65 = ageOver65,
        ageOver68 = ageOver68,
        issuanceDate = LocalDateOrInstant.LocalDate(issueDate),
        expiryDate = LocalDateOrInstant.LocalDate(expiryDate),
        issuingAuthority = issuingAuthority,
        issuingCountry = issuingCountry,
    ).also { Napier.v("toEuPidCredential returns $it") },
    expiration = exp,
    scheme = scheme,
    subjectPublicKey = pubKey,
    userInfo = this,
)


fun OidcUserInfoExtended.buildEupidClaimsSdJwt(useSd: Boolean) =
    with(EuPidSdJwtScheme.SdJwtAttributes) {
        val (postCode, city, state, street, locator) = addressOrRandom
        val (_, ourBirthCity, ourBirthState, _) = randomAddress
        val country = userInfo.address?.country ?: fallbackAddressCountry
        val formatted = userInfo.address?.formatted ?: formatAddress(street, locator, postCode, city)
        listOfNotNull(
            claim(FAMILY_NAME, useSd) { userInfo.familyName },
            claim(GIVEN_NAME, useSd) { userInfo.givenName },
            claim(BIRTH_DATE, useSd) { dateOfBirth },
            claim(PORTRAIT, useSd) { portrait?.let { "data:image/jpeg;base64,${it.encodeToString(Base64Strict)}" } },
            claim(FAMILY_NAME_BIRTH, useSd) { userInfo.familyName },
            claim(GIVEN_NAME_BIRTH, useSd) { userInfo.givenName },
            claim(PREFIX_PLACE_OF_BIRTH, useSd) {
                with(EuPidSdJwtScheme.SdJwtAttributes.PlaceOfBirth) {
                    listOf(
                        claim(LOCALITY, useSd) { ourBirthCity },
                        claim(COUNTRY, useSd) { country },
                        claim(REGION, useSd) { ourBirthState },
                    )
                }
            },
            claim(PREFIX_ADDRESS, useSd) {
                with(EuPidSdJwtScheme.SdJwtAttributes.Address) {
                    listOf(
                        claim(FORMATTED, useSd) { formatted },
                        claim(COUNTRY, useSd) { country },
                        claim(REGION, useSd) { state },
                        claim(LOCALITY, useSd) { city },
                        claim(POSTAL_CODE, useSd) { postCode },
                        claim(STREET, useSd) { street },
                        claim(HOUSE_NUMBER, useSd) { locator },
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
    with(EuPidScheme.Attributes) {
        val (postCode, city, state, street, locator) = addressOrRandom
        val (_, ourBirthCity, ourBirthState, _) = randomAddress
        val country = userInfo.address?.country ?: fallbackAddressCountry
        val formatted = userInfo.address?.formatted ?: formatAddress(street, locator, postCode, city)
        listOfNotNull(
            claim(FAMILY_NAME, useSd) { userInfo.familyName },
            claim(GIVEN_NAME, useSd) { userInfo.givenName },
            claim(BIRTH_DATE, useSd) { dateOfBirth },
            claim(PLACE_OF_BIRTH, useSd) { PlaceOfBirth(fallbackBirthCountry, ourBirthState, ourBirthCity) },
            claim(NATIONALITY, useSd) { setOf(nationality) },
            claim(RESIDENT_ADDRESS, useSd) { formatted },
            claim(RESIDENT_COUNTRY, useSd) { country },
            claim(RESIDENT_STATE, useSd) { state },
            claim(RESIDENT_CITY, useSd) { city },
            claim(RESIDENT_POSTAL_CODE, useSd) { postCode },
            claim(RESIDENT_STREET, useSd) { street },
            claim(RESIDENT_HOUSE_NUMBER, useSd) { locator.toString() },
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
            locator = street!!.substringAfter(" ").toIntOrNull() ?: randomAddressLocator
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
            claim(TAX_NUMBER, useSd) { "ATU12345678" },
            claim(AFFILIATION_COUNTRY, useSd) { "AT" },
            claim(REGISTERED_GIVEN_NAME, useSd) { userInfo.givenName },
            claim(REGISTERED_FAMILY_NAME, useSd) { userInfo.familyName },
            claim(RESIDENT_ADDRESS, useSd) {
                addressOrRandom
                    .let { it.street + " " + it.locator + ", " + it.postCode + " " + it.city }
            },
            claim(BIRTH_DATE, useSd) { dateOfBirth },
            claim(CHURCH_TAX_ID, useSd) { "ATU13339991" },
            claim(IBAN, useSd) { "AT023200051286875134" },
            claim(PID_ID, useSd) { "PID12345678" },
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
        val (postCode, city, state, street, locator) = addressOrRandom
        val country = userInfo.address?.country ?: fallbackAddressCountry
        val fullAddress = formatAddress(street, locator, postCode, city)
        listOfNotNull(
            claim(FAMILY_NAME, useSd) { userInfo.familyName },
            claim(GIVEN_NAME, useSd) { userInfo.givenName },
            claim(BIRTH_DATE, useSd) { dateOfBirth },
            claim(RESIDENCE_ADDRESS, useSd) {
                with(CertificateOfResidenceDataElements.Address) {
                    listOf(
                        claim(THOROUGHFARE, useSd) { street },
                        claim(LOCATOR_DESIGNATOR, useSd) { locator },
                        claim(POST_CODE, useSd) { postCode },
                        claim(POST_NAME, useSd) { city },
                        claim(ADMIN_UNIT_L_1, useSd) { country },
                        claim(ADMIN_UNIT_L_2, useSd) { state },
                        claim(FULL_ADDRESS, useSd) { fullAddress },
                    )
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
        val (postCode, city, state, street, locator) = addressOrRandom
        val country = userInfo.address?.country ?: fallbackAddressCountry
        val formatted = userInfo.address?.formatted ?: formatAddress(street, locator, postCode, city)
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
            claim(RESIDENT_ADDRESS, useSd) { formatted },
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
            claim(RESIDENT_CITY, useSd) { city },
            claim(RESIDENT_STATE, useSd) { state },
            claim(RESIDENT_POSTAL_CODE, useSd) { postCode },
            claim(RESIDENT_COUNTRY, useSd) { country },
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

private fun formatAddress(street: String, locator: Int, postalCode: String, city: String) =
    "$street $locator, $postalCode $city"

private fun claim(key: String, useSd: Boolean, value: () -> Any?): ClaimToBeIssued? =
    value()?.let { ClaimToBeIssued(key, it, useSd) }
