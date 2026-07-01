package at.asitplus.wallet.backend.config

import at.asitplus.iso.IssuerSignedItem
import at.asitplus.openid.OidcAddressClaim
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.io.Base64Strict
import at.asitplus.wallet.eupid.EU_PID_DOCTYPE
import at.asitplus.wallet.eupid.EuPidDataElements
import at.asitplus.wallet.eupid.PlaceOfBirth
import at.asitplus.wallet.eupidsdjwt.EU_PID_SD_JWT_VCT
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtDataElements
import at.asitplus.wallet.lib.agent.ClaimToBeIssued
import at.asitplus.wallet.lib.agent.ClaimToBeIssuedArrayElement
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.data.IsoMdocCredentialScheme
import at.asitplus.wallet.lib.data.LocalDateOrInstant
import at.asitplus.wallet.lib.data.SdJwtCredentialScheme
import at.asitplus.wallet.mdl.MDL_DOCTYPE
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlin.random.Random
import kotlin.time.Instant

/**
 * Builds the (fabricated) claims for each credential, dispatched by the credential's vct / ISO docType. Claim
 * names come from typed constants: vck's [EuPidSdJwtDataElements] / [EuPidDataElements] /
 * [MobileDrivingLicenceDataElements] for EU PID and mDL, and the local [TaxIdClaims], [PowerOfRepresentationClaims],
 * [CertificateOfResidenceClaims], [EhicClaims] and [AgeVerificationClaims] for the credentials vck ships no
 * constants for.
 */
fun SdJwtCredentialScheme.buildSdJwtClaims(
    userInfo: OidcUserInfoExtended,
    iss: Instant,
    exp: Instant,
    subjectPublicKey: CryptoPublicKey,
) = CredentialToBeIssued.VcSd(
    claims = when (sdJwtType) {
        EU_PID_SD_JWT_VCT -> userInfo.buildEupidClaimsSdJwt(true)
        TaxIdClaims.VCT -> userInfo.buildTaxIdClaims(iss, exp, false)
        PowerOfRepresentationClaims.VCT -> userInfo.buildPorClaims(iss, exp, false)
        CertificateOfResidenceClaims.VCT -> userInfo.buildCorClaims(iss, exp, true)
        EhicClaims.VCT -> userInfo.buildEhicClaims(iss, exp, false)
        else -> TODO("$this is not implemented in buildSdJwtClaims()")
    },
    expiration = exp,
    scheme = this,
    subjectPublicKey = subjectPublicKey,
    userInfo = userInfo,
).also { Napier.v("${this}.buildSdJwtClaims returns $it") }

fun IsoMdocCredentialScheme.buildIsoClaims(
    userInfo: OidcUserInfoExtended,
    exp: Instant,
    subjectPublicKey: CryptoPublicKey,
) = CredentialToBeIssued.Iso(
    issuerSignedItems = when (this.isoDocType) {
        EU_PID_DOCTYPE -> userInfo.buildEupidClaims(true)
        MDL_DOCTYPE -> userInfo.buildMdlClaims(true)
        AV_DOCTYPE -> userInfo.buildAgeClaims(true)
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

fun OidcUserInfoExtended.buildEupidClaimsSdJwt(useSd: Boolean) =
    with(EuPidSdJwtDataElements) {
        listOfNotNull(
            claim(FAMILY_NAME, useSd) { userInfo.familyName },
            claim(GIVEN_NAME, useSd) { userInfo.givenName },
            claim(BIRTH_DATE, useSd) { dateOfBirth },
            claim(
                PORTRAIT,
                useSd
            ) { portrait?.let { "data:image/jpeg;base64,${it.encodeToString(Base64Strict)}" } },
            claim(FAMILY_NAME_BIRTH, useSd) { userInfo.familyName },
            claim(GIVEN_NAME_BIRTH, useSd) { userInfo.givenName },
            claim(PREFIX_PLACE_OF_BIRTH, useSd) {
                with(EuPidSdJwtDataElements.PlaceOfBirth) {
                    randomAddress.let { birthAddress ->
                        listOf(
                            claim(LOCALITY, useSd) { birthAddress.city },
                            claim(COUNTRY, useSd) { birthAddress.country },
                            claim(REGION, useSd) { birthAddress.state },
                        )
                    }
                }
            },
            claim(PREFIX_ADDRESS, useSd) {
                with(EuPidSdJwtDataElements.Address) {
                    addressOrRandom.let { address ->
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
        addressOrRandom.let { address ->
            listOfNotNull(
                claim(FAMILY_NAME, useSd) { userInfo.familyName },
                claim(GIVEN_NAME, useSd) { userInfo.givenName },
                claim(BIRTH_DATE, useSd) { dateOfBirth },
                claim(PLACE_OF_BIRTH, useSd) {
                    randomAddress.let { birthAddress ->
                        PlaceOfBirth(
                            country = birthAddress.country,
                            region = birthAddress.state,
                            locality = birthAddress.city
                        )
                    }
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
    with(PowerOfRepresentationClaims) {
        listOfNotNull(
            claim(LEGAL_PERSON_IDENTIFIER, useSd) { legalPersonIdentifier },
            claim(LEGAL_NAME, useSd) { legalName },
            claim(FULL_POWERS, useSd) { true },
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
    with(TaxIdClaims) {
        listOfNotNull(
            claim(TAX_NUMBER, useSd) { randomTaxNumber },
            claim(AFFILIATION_COUNTRY, useSd) { fallbackAddressCountry },
            claim(REGISTERED_GIVEN_NAME, useSd) { userInfo.givenName },
            claim(REGISTERED_FAMILY_NAME, useSd) { userInfo.familyName },
            claim(RESIDENT_ADDRESS, useSd) { addressOrRandom.formatted },
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

fun OidcUserInfoExtended.buildEhicClaims(iss: Instant, exp: Instant, useSd: Boolean) =
    with(EhicClaims) {
        randomIdentifier.let { issuingAuthorityId ->
            randomIdentifier.let { authenticSourceId ->
                listOfNotNull(
                    claim(ISSUING_COUNTRY, useSd) { issuingCountry },
                    claim(PERSONAL_ADMINISTRATIVE_NUMBER, useSd) { socialSecurityNumber },
                    claim(DOCUMENT_NUMBER, useSd) { randomIdentifier },
                    claim(ISSUING_AUTHORITY_ID, useSd) { issuingAuthorityId },
                    claim(ISSUING_AUTHORITY_NAME, useSd) { issuingAuthority },
                    claim(ISSUING_AUTHORITY, useSd) {
                        listOf(
                            claim(ID, useSd) { issuingAuthorityId },
                            claim(NAME, useSd) { issuingAuthority }
                        )
                    },
                    claim(AUTHENTIC_SOURCE_ID, useSd) { authenticSourceId },
                    claim(AUTHENTIC_SOURCE_NAME, useSd) { authenticSource },
                    claim(AUTHENTIC_SOURCE, useSd) {
                        listOf(
                            claim(ID, useSd) { authenticSourceId },
                            claim(NAME, useSd) { authenticSource }
                        )
                    },
                    claim(DATE_OF_ISSUANCE, useSd) { iss.toLocalDate() },
                    claim(DATE_OF_EXPIRY, useSd) { exp.toLocalDate() },
                    claim(STARTING_DATE, useSd) { expiryDate },
                    claim(ENDING_DATE, useSd) { expiryDate },
                )
            }
        }
    }

fun OidcUserInfoExtended.buildCorClaims(iss: Instant, exp: Instant, useSd: Boolean) =
    with(CertificateOfResidenceClaims) {
        listOfNotNull(
            claim(FAMILY_NAME, useSd) { userInfo.familyName },
            claim(GIVEN_NAME, useSd) { userInfo.givenName },
            claim(BIRTH_DATE, useSd) { dateOfBirth },
            claim(RESIDENCE_ADDRESS, useSd) {
                with(addressOrRandom) {
                    with(CertificateOfResidenceClaims.ResidenceAddress) {
                        listOf(
                            claim(THOROUGHFARE, useSd) { street },
                            claim(LOCATOR_DESIGNATOR, useSd) { locator },
                            claim(POST_CODE, useSd) { postCode },
                            claim(POST_NAME, useSd) { city },
                            claim(ADMIN_UNIT_L1, useSd) { country },
                            claim(ADMIN_UNIT_L2, useSd) { state },
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

fun OidcUserInfoExtended.buildMdlClaims(useSd: Boolean): List<ClaimToBeIssued> =
    with(MobileDrivingLicenceDataElements) {
        addressOrRandom.let { address ->
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
                claim(NATIONALITY, useSd) { nationality },
                claim(RESIDENT_CITY, useSd) { address.city },
                claim(RESIDENT_STATE, useSd) { address.state },
                claim(RESIDENT_POSTAL_CODE, useSd) { address.postCode },
                claim(RESIDENT_COUNTRY, useSd) { address.country },
                claim(FAMILY_NAME_NATIONAL_CHARACTER, useSd) { userInfo.familyName + " 🦄" },
                claim(GIVEN_NAME_NATIONAL_CHARACTER, useSd) { userInfo.givenName + " 🦄" },
                claim(SIGNATURE_USUAL_MARK, useSd) { pictureTripleX },
                claim(BIOMETRIC_TEMPLATE_FACE, useSd) { pictureTripleX },
                claim(BIOMETRIC_TEMPLATE_FINGER, useSd) { pictureTripleX },
                claim(BIOMETRIC_TEMPLATE_SIGNATURE_SIGN, useSd) { pictureTripleX },
                claim(BIOMETRIC_TEMPLATE_IRIS, useSd) { pictureTripleX },
            )
        }
    }

fun OidcUserInfoExtended.buildAgeClaims(useSd: Boolean) =
    with(AgeVerificationClaims) {
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

private fun claim(key: String, useSd: Boolean, value: () -> Any?): ClaimToBeIssued? =
    value()?.let { ClaimToBeIssued(key, it, useSd) }
