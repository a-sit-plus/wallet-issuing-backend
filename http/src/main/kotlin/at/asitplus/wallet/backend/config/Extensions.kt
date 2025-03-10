package at.asitplus.wallet.backend.config

import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.wallet.companyregistration.CompanyRegistrationDataElements
import at.asitplus.wallet.companyregistration.CompanyRegistrationScheme
import at.asitplus.wallet.cor.CertificateOfResidenceDataElements
import at.asitplus.wallet.cor.CertificateOfResidenceScheme
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.eupid.EuPidScheme.Attributes.BIRTH_PLACE
import at.asitplus.wallet.eupid.EuPidScheme.Attributes.EMAIL_ADDRESS
import at.asitplus.wallet.eupid.EuPidScheme.Attributes.MOBILE_PHONE_NUMBER
import at.asitplus.wallet.eupid.IsoIec5218Gender
import at.asitplus.wallet.healthid.HealthIdScheme
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.lib.agent.ClaimToBeIssued
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.iso.IssuerSignedItem
import at.asitplus.wallet.mdl.DrivingPrivilege
import at.asitplus.wallet.mdl.IsoSexEnum
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
import at.asitplus.wallet.por.PowerOfRepresentationDataElements
import at.asitplus.wallet.por.PowerOfRepresentationScheme
import at.asitplus.wallet.taxid.TaxIdScheme
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.*
import java.nio.charset.Charset
import java.util.*
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.random.nextUInt

fun ClaimToBeIssued.buildIssuerSignedItem(index: Int) =
    IssuerSignedItem(
        digestId = index.toUInt(),
        random = Random.nextBytes(16),
        elementIdentifier = name,
        elementValue = value
    )

fun ConstantIndex.CredentialScheme.buildClaims(
    representation: ConstantIndex.CredentialRepresentation,
    claims: Collection<String>?,
    userInfo: OidcUserInfoExtended,
    iss: Instant,
    exp: Instant,
    loader: EPrescriptionLoader,
): List<ClaimToBeIssued> =
    when (this) {
        is IdAustriaScheme -> userInfo.buildIdaClaims(claims)
        is EuPidScheme -> /* // TODO Use this once ARF PR#160 is through
        if (representation == CredentialRepresentation.SD_JWT)
            userInfo.buildEupidClaimsSdJwt(claims, iss, exp)
        else*/
            userInfo.buildEupidClaims(claims, iss, exp)
        is HealthIdScheme -> userInfo.buildHealthIdClaims(claims, iss, exp, loader)
        is TaxIdScheme -> userInfo.buildTaxIdClaims(claims, iss, exp)
        is MobileDrivingLicenceScheme -> userInfo.buildMdlClaims(claims)
        is PowerOfRepresentationScheme -> userInfo.buildPorClaims(claims, iss, exp)
        is CertificateOfResidenceScheme -> userInfo.buildCorClaims(claims, iss, exp)
        is CompanyRegistrationScheme -> userInfo.buildCompanyRegistrationClaims(claims, iss, exp)
        else -> TODO("$this is not implemented in buildClaims()")
    }.also { Napier.v("${this}.buildClaims returns $it") }

fun List<ClaimToBeIssued>.toIsoClaims(
    pubKey: CryptoPublicKey,
    exp: Instant,
    scheme: ConstantIndex.CredentialScheme,
) = CredentialToBeIssued.Iso(
    issuerSignedItems = this
        .mapIndexed { idx, it -> it.buildIssuerSignedItem(idx) },
    expiration = exp,
    scheme = scheme,
    subjectPublicKey = pubKey,
)

fun List<ClaimToBeIssued>.toSdJwtClaims(
    pubKey: CryptoPublicKey,
    exp: Instant,
    scheme: ConstantIndex.CredentialScheme,
) = CredentialToBeIssued.VcSd(
    claims = this,
    expiration = exp,
    scheme = scheme,
    subjectPublicKey = pubKey,
)

fun OidcUserInfoExtended.toIdaCredential(
    pubKey: CryptoPublicKey,
    exp: Instant,
    scheme: ConstantIndex.CredentialScheme,
) = CredentialToBeIssued.VcJwt(
    subject = IdAustriaCredential(
        id = pubKey.didEncoded,
        bpk = bpk,
        firstname = userInfo.givenName ?: "N/A",
        lastname = userInfo.familyName ?: "N/A",
        dateOfBirth = dateOfBirth,
        portrait = portrait,
        mainAddress = mainAddress,
        ageOver14 = ageOver14,
        ageOver16 = ageOver16,
        ageOver18 = ageOver18,
        ageOver21 = ageOver21,
    ).also { Napier.v("idaVcJwt returns $it") },
    expiration = exp,
    scheme = scheme,
    subjectPublicKey = pubKey,
)

fun OidcUserInfoExtended.toEuPidCredential(
    pubKey: CryptoPublicKey,
    iss: Instant,
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
        ageOver14 = ageOver14,
        ageOver16 = ageOver16,
        ageOver18 = ageOver18,
        ageOver21 = ageOver21,
        issuanceDate = iss,
        expiryDate = exp,
        issuingAuthority = issuingAuthority,
        issuingCountry = issuingCountry,
    ).also { Napier.v("eupidVcJwt returns $it") },
    expiration = exp,
    scheme = scheme,
    subjectPublicKey = pubKey
)

fun OidcUserInfoExtended.buildIdaClaims(claims: Collection<String>?) =
    with(IdAustriaScheme.Attributes) {
        listOfNotNull(
            claims.whenRequested(BPK) { bpk },
            claims.whenRequested(FIRSTNAME) { userInfo.givenName },
            claims.whenRequested(LASTNAME) { userInfo.familyName },
            claims.whenRequested(DATE_OF_BIRTH) { dateOfBirth },
            claims.whenRequested(PORTRAIT) { portrait },
            claims.whenRequested(MAIN_ADDRESS) { mainAddress },
            claims.whenRequested(AGE_OVER_14) { ageOver14 },
            claims.whenRequested(AGE_OVER_16) { ageOver16 },
            claims.whenRequested(AGE_OVER_18) { ageOver18 },
            claims.whenRequested(AGE_OVER_21) { ageOver21 },
            claims.whenRequested(GENDER) { genderText },
        )
    }

fun OidcUserInfoExtended.buildEupidClaimsSdJwt(claims: Collection<String>?, iss: Instant, exp: Instant) =
    with(EuPidScheme.SdJwtAttributes) {
        val (postCode, city, state, street, locator) = addressOrRandom()
        val (_, ourBirthCity, ourBirthState, ourBirthStreet) = randomAddress()
        val country = userInfo.address?.country ?: fallbackAddressCountry
        val formatted = userInfo.address?.formatted ?: formatAddress(street, locator, postCode, city)
        listOfNotNull(
            claims.whenRequestedIssueIsoName(FAMILY_NAME) { userInfo.familyName },
            claims.whenRequestedIssueIsoName(GIVEN_NAME) { userInfo.givenName },
            claims.whenRequestedIssueIsoName(BIRTH_DATE) { dateOfBirth },
            claims.whenRequestedIssueIsoName(PORTRAIT) { portrait },
            claims.whenRequestedIssueIsoName(PREFIX_AGE_EQUAL_OR_OVER) {
                with(EuPidScheme.SdJwtAttributes.AgeEqualOrOver) {
                    listOf(
                        ClaimToBeIssued(EQUAL_OR_OVER_12, ageOver12),
                        ClaimToBeIssued(EQUAL_OR_OVER_14, ageOver14),
                        ClaimToBeIssued(EQUAL_OR_OVER_16, ageOver16),
                        ClaimToBeIssued(EQUAL_OR_OVER_18, ageOver18),
                        ClaimToBeIssued(EQUAL_OR_OVER_21, ageOver21),
                    )
                }
            },
            claims.whenRequestedIssueIsoName(AGE_EQUAL_OR_OVER_12) { ageOver12 },
            claims.whenRequestedIssueIsoName(AGE_EQUAL_OR_OVER_14) { ageOver14 },
            claims.whenRequestedIssueIsoName(AGE_EQUAL_OR_OVER_16) { ageOver16 },
            claims.whenRequestedIssueIsoName(AGE_EQUAL_OR_OVER_18) { ageOver18 },
            claims.whenRequestedIssueIsoName(AGE_EQUAL_OR_OVER_21) { ageOver21 },
            claims.whenRequestedIssueIsoName(AGE_IN_YEARS) { ageInYears },
            claims.whenRequestedIssueIsoName(AGE_BIRTH_YEAR) { dateOfBirth.year.toUInt() },
            claims.whenRequestedIssueIsoName(FAMILY_NAME_BIRTH) { userInfo.familyName },
            claims.whenRequestedIssueIsoName(GIVEN_NAME_BIRTH) { userInfo.givenName },
            claims.whenRequestedIssueIsoName(PREFIX_PLACE_OF_BIRTH) {
                with(EuPidScheme.SdJwtAttributes.PlaceOfBirth) {
                    listOf(
                        ClaimToBeIssued(LOCALITY, ourBirthCity),
                    )
                }
            },
            claims.whenRequested(BIRTH_PLACE) { ourBirthCity },
            claims.whenRequestedIssueIsoName(PLACE_OF_BIRTH_LOCALITY) { ourBirthCity },
            claims.whenRequestedIssueIsoName(PREFIX_PLACE_OF_BIRTH) {
                with(EuPidScheme.SdJwtAttributes.Address) {
                    listOf(
                        ClaimToBeIssued(FORMATTED, formatted),
                        ClaimToBeIssued(COUNTRY, country),
                        ClaimToBeIssued(REGION, state),
                        ClaimToBeIssued(LOCALITY, city),
                        ClaimToBeIssued(POSTAL_CODE, postCode),
                        ClaimToBeIssued(STREET, street),
                        ClaimToBeIssued(HOUSE_NUMBER, locator.toString()),
                    )
                }
            },
            claims.whenRequestedIssueIsoName(ADDRESS_FORMATTED) { formatted },
            claims.whenRequestedIssueIsoName(ADDRESS_COUNTRY) { country },
            claims.whenRequestedIssueIsoName(ADDRESS_REGION) { state },
            claims.whenRequestedIssueIsoName(ADDRESS_LOCALITY) { city },
            claims.whenRequestedIssueIsoName(ADDRESS_POSTAL_CODE) { postCode },
            claims.whenRequestedIssueIsoName(ADDRESS_STREET) { street },
            claims.whenRequestedIssueIsoName(ADDRESS_HOUSE_NUMBER) { locator.toString() },
            claims.whenRequestedIssueIsoName(GENDER) { genderText },
            claims.whenRequestedIssueIsoName(EuPidScheme.Attributes.NATIONALITY) { setOf(nationality) },
            claims.whenRequestedIssueIsoName(ISSUANCE_DATE) { iss },
            claims.whenRequestedIssueIsoName(EXPIRY_DATE) { exp },
            claims.whenRequestedIssueIsoName(ISSUING_AUTHORITY) { issuingAuthority },
            claims.whenRequestedIssueIsoName(DOCUMENT_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequestedIssueIsoName(ISSUING_COUNTRY) { issuingCountry },
            claims.whenRequestedIssueIsoName(ISSUING_JURISDICTION) { issuingJurisdiction },
            claims.whenRequestedIssueIsoName(PERSONAL_ADMINISTRATIVE_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequestedIssueIsoName(EMAIL_ADDRESS) { email },
            claims.whenRequestedIssueIsoName(MOBILE_PHONE_NUMBER) { phoneNumber },
        )
    }

fun OidcUserInfoExtended.buildEupidClaims(claims: Collection<String>?, iss: Instant, exp: Instant) =
    with(EuPidScheme.Attributes) {
        val (postCode, city, state, street, locator) = addressOrRandom()
        val (_, ourBirthCity, ourBirthState, ourBirthStreet) = randomAddress()
        val country = userInfo.address?.country ?: fallbackAddressCountry
        val formatted = userInfo.address?.formatted ?: formatAddress(street, locator, postCode, city)
        listOfNotNull(
            claims.whenRequested(FAMILY_NAME) { userInfo.familyName },
            claims.whenRequested(GIVEN_NAME) { userInfo.givenName },
            claims.whenRequested(BIRTH_DATE) { dateOfBirth },
            claims.whenRequested(BIRTH_PLACE) { ourBirthCity },
            claims.whenRequested(NATIONALITY) { setOf(nationality) },
            claims.whenRequested(RESIDENT_ADDRESS) { formatted },
            claims.whenRequested(RESIDENT_COUNTRY) { country },
            claims.whenRequested(RESIDENT_STATE) { state },
            claims.whenRequested(RESIDENT_CITY) { city },
            claims.whenRequested(RESIDENT_POSTAL_CODE) { postCode },
            claims.whenRequested(RESIDENT_STREET) { street },
            claims.whenRequested(RESIDENT_HOUSE_NUMBER) { locator.toString() },
            claims.whenRequested(PERSONAL_ADMINISTRATIVE_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequested(PORTRAIT) { portrait },
            claims.whenRequested(FAMILY_NAME_BIRTH) { userInfo.familyName },
            claims.whenRequested(GIVEN_NAME_BIRTH) { userInfo.givenName },
            claims.whenRequested(SEX) { gender.code },
            claims.whenRequested(EMAIL_ADDRESS) { email },
            claims.whenRequested(MOBILE_PHONE_NUMBER) { phoneNumber },
            claims.whenRequested(EXPIRY_DATE) { exp },
            claims.whenRequested(ISSUING_AUTHORITY) { issuingAuthority },
            claims.whenRequested(ISSUING_COUNTRY) { issuingCountry },
            claims.whenRequested(DOCUMENT_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequested(ISSUING_JURISDICTION) { issuingJurisdiction },
            claims.whenRequested(ISSUANCE_DATE) { iss },
            claims.whenRequested(AGE_OVER_12) { ageOver12 },
            claims.whenRequested(AGE_OVER_14) { ageOver14 },
            claims.whenRequested(AGE_OVER_16) { ageOver16 },
            claims.whenRequested(AGE_OVER_18) { ageOver18 },
            claims.whenRequested(AGE_OVER_21) { ageOver21 },
            claims.whenRequested(AGE_IN_YEARS) { ageInYears },
            claims.whenRequested(AGE_BIRTH_YEAR) { dateOfBirth.year.toUInt() },
        )
    }

private fun OidcUserInfoExtended.addressOrRandom() = if (userInfo.address?.postalCode != null
    && userInfo.address?.locality != null
    && userInfo.address?.region != null
    && userInfo.address?.street != null
) {
    Address(
        postCode = userInfo.address!!.postalCode!!,
        city = userInfo.address!!.locality!!,
        state = userInfo.address!!.region!!,
        street = userInfo.address!!.street!!.substringBefore(" "),
        locator = userInfo.address!!.street!!.substringAfter(" ").toIntOrNull() ?: randomAddressLocator()
    )
} else {
    getClaimAsString("urn:eidgvat:attributes.mainAddress")?.let { idaAddress ->
        runCatching {
            val json = Json.parseToJsonElement(
                idaAddress.decodeToByteArray(Base64()).toString(Charset.defaultCharset())
            ) as? JsonObject
            val postCode = json.getPrimitiveContent("Postleitzahl")
            val city = json.getPrimitiveContent("Ortschaft")
            val street = json.getPrimitiveContent("Strasse")
            val locator = json.getPrimitiveContent("Hausnummer")
            if (postCode != null && city != null && street != null && locator != null) {
                Address(
                    postCode = postCode,
                    city = city,
                    state = postCode.toState(),
                    street = street,
                    locator = locator.toIntOrNull() ?: randomAddressLocator()
                )
            } else {
                null
            }
        }.getOrNull()
    } ?: randomAddress()
}

private fun JsonObject?.getPrimitiveContent(key: String) = (this?.get(key) as? JsonPrimitive)?.content

fun OidcUserInfoExtended.buildPorClaims(claims: Collection<String>?, iss: Instant, exp: Instant) =
    with(PowerOfRepresentationDataElements) {
        listOfNotNull(
            claims.whenRequested(LEGAL_PERSON_IDENTIFIER, false) { legalPersonIdentifier },
            claims.whenRequested(LEGAL_NAME, false) { legalName },
            claims.whenRequested(FULL_POWERS, false) { true },
            //claims.whenRequested(E_SERVICE, false) { eService },
            claims.whenRequested(EFFECTIVE_FROM_DATE, false) { iss },
            claims.whenRequested(EFFECTIVE_UNTIL_DATE, false) { exp },
            claims.whenRequested(ISSUANCE_DATE, false) { iss },
            claims.whenRequested(EXPIRY_DATE, false) { exp },
            claims.whenRequested(ISSUING_AUTHORITY, false) { issuingAuthority },
            claims.whenRequested(ISSUING_COUNTRY, false) { issuingCountry },
            claims.whenRequested(ISSUING_JURISDICTION, false) { issuingJurisdiction },
            claims.whenRequested(DOCUMENT_NUMBER, false) { UUID.randomUUID().toString() },
            claims.whenRequested(ADMINISTRATIVE_NUMBER, false) { UUID.randomUUID().toString() },
        )
    }

fun OidcUserInfoExtended.buildTaxIdClaims(claims: Collection<String>?, iss: Instant, exp: Instant) =
    with(TaxIdScheme.Attributes) {
        listOfNotNull(
            claims.whenRequested(TAX_NUMBER, false) { "ATU12345678" },
            claims.whenRequested(AFFILIATION_COUNTRY, false) { "AT" },
            claims.whenRequested(REGISTERED_GIVEN_NAME, false) { userInfo.givenName },
            claims.whenRequested(REGISTERED_FAMILY_NAME, false) { userInfo.familyName },
            claims.whenRequested(RESIDENT_ADDRESS, false) { addressOrRandom().let { it.street + " " + it.locator + ", " + it.postCode + " " + it.city } },
            claims.whenRequested(BIRTH_DATE, false) { dateOfBirth },
            claims.whenRequested(CHURCH_TAX_ID, false) { "ATU13339991" },
            claims.whenRequested(IBAN, false) { "AT023200051286875134" },
            claims.whenRequested(PID_ID, false) { "PID12345678" },
            claims.whenRequested(ISSUANCE_DATE, false) { iss },
            claims.whenRequested(EXPIRY_DATE, false) { exp },
            claims.whenRequested(ISSUING_AUTHORITY, false) { issuingAuthority },
            claims.whenRequested(ISSUING_COUNTRY, false) { issuingCountry },
            claims.whenRequested(ISSUING_JURISDICTION, false) { issuingJurisdiction },
            claims.whenRequested(DOCUMENT_NUMBER, false) { UUID.randomUUID().toString() },
            claims.whenRequested(ADMINISTRATIVE_NUMBER, false) { UUID.randomUUID().toString() },
        )
    }

fun OidcUserInfoExtended.buildHealthIdClaims(claims: Collection<String>?, iss: Instant, exp: Instant, loader: EPrescriptionLoader) =
    with(HealthIdScheme.Attributes) {
        val ottElement =
            loader.load(bpk, userInfo.givenName!!, userInfo.familyName!!, userInfo.birthDate!!).getOrNull()?.data
                ?: throw IllegalArgumentException("No data from EPrescriptionLoader")
        listOfNotNull(
            claims.whenRequested(ONE_TIME_TOKEN, false) { ottElement.oneTimeToken },
            claims.whenRequested(AFFILIATION_COUNTRY, false) { ottElement.countryCode },
            claims.whenRequested(ISSUE_DATE, false) { iss },
            claims.whenRequested(EXPIRY_DATE, false) { exp },
            claims.whenRequested(ISSUING_AUTHORITY, false) { issuingAuthority },
            claims.whenRequested(ISSUING_COUNTRY, false) { issuingCountry },
            claims.whenRequested(ISSUING_JURISDICTION, false) { issuingJurisdiction },
            claims.whenRequested(DOCUMENT_NUMBER, false) { UUID.randomUUID().toString() },
            claims.whenRequested(ADMINISTRATIVE_NUMBER, false) { UUID.randomUUID().toString() },
        )
    }


fun OidcUserInfoExtended.buildCompanyRegistrationClaims(claims: Collection<String>?, iss: Instant, exp: Instant) =
    with(CompanyRegistrationDataElements) {
        listOfNotNull(
            claims.whenRequested(COMPANY_NAME) { legalName },
            claims.whenRequested(COMPANY_TYPE) { "Einzelunternehmen" },
            claims.whenRequested(COMPANY_STATUS) { "economically active" },
            claims.whenRequested(COMPANY_ACTIVITY) {
                with(CompanyRegistrationDataElements.CompanyActivity) {
                    listOf(
                        ClaimToBeIssued(NACE_CODE, "J62"),
                        //ClaimToBeIssued(ACTIVITY_DESCRIPTION, "7500")
                    )
                }
            },
            claims.whenRequested(REGISTRATION_DATE) { LocalDate(2015, 6, 25) },
            //claims.whenRequested(COMPANY_END_DATE) { LocalDate(2025, Random.nextInt(1, 12), Random.nextInt(1, 28)) },
            claims.whenRequested(COMPANY_EUID) { "ATCHCUSP.69743824" },
            claims.whenRequested(VAT_NUMBER) { "ATU69743824" },
            claims.whenRequested(COMPANY_CONTACT_DATA) {
                with(CompanyRegistrationDataElements.ContactData) {
                    listOf(
                        ClaimToBeIssued(EMAIL, "office@a-sit.at"),
                        ClaimToBeIssued(TELEPHONE, "+43-555-${Random.nextInt(1, 9999)}")
                    )
                }
            },
            claims.whenRequested(REGISTERED_ADDRESS) {
                with(CompanyRegistrationDataElements.Address) {
                    listOf(
                        ClaimToBeIssued(THOROUGHFARE, "Seidlgasse"),
                        ClaimToBeIssued(LOCATOR_DESIGNATOR, "22/9"),
                        ClaimToBeIssued(POST_CODE, "1030"),
                        ClaimToBeIssued(POST_NAME, "Wien"),
                        ClaimToBeIssued(ADMIN_UNIT_L_1, "AT"),
                        ClaimToBeIssued(ADMIN_UNIT_L_2, "Wien")
                    )
                }
            },
        )
    }

fun OidcUserInfoExtended.buildCorClaims(claims: Collection<String>?, iss: Instant, exp: Instant) =
    with(CertificateOfResidenceDataElements) {
        val (postCode, city, state, street, locator) = addressOrRandom()
        val country = userInfo.address?.country ?: fallbackAddressCountry
        val fullAddress = formatAddress(street, locator, postCode, city)
        listOfNotNull(
            claims.whenRequested(FAMILY_NAME) { userInfo.familyName },
            claims.whenRequested(GIVEN_NAME) { userInfo.givenName },
            claims.whenRequested(BIRTH_DATE) { dateOfBirth },
            claims.whenRequested(RESIDENCE_ADDRESS) {
                with(CertificateOfResidenceDataElements.Address) {
                    listOf(
                        claim(THOROUGHFARE) { street },
                        claim(LOCATOR_DESIGNATOR) { locator },
                        claim(POST_CODE) { postCode },
                        claim(POST_NAME) { city },
                        claim(ADMIN_UNIT_L_1) { country },
                        claim(ADMIN_UNIT_L_2) { state },
                        claim(FULL_ADDRESS) { fullAddress },
                    )
                }
            },
            claims.whenRequested(RESIDENCE_ADDRESS_THOROUGHFARE) { street },
            claims.whenRequested(RESIDENCE_ADDRESS_LOCATOR_DESIGNATOR) { locator },
            claims.whenRequested(RESIDENCE_ADDRESS_POST_CODE) { postCode },
            claims.whenRequested(RESIDENCE_ADDRESS_POST_NAME) { city },
            claims.whenRequested(RESIDENCE_ADDRESS_ADMIN_UNIT_L_1) { country },
            claims.whenRequested(RESIDENCE_ADDRESS_ADMIN_UNIT_L_2) { state },
            claims.whenRequested(RESIDENCE_ADDRESS_FULL_ADDRESS) { fullAddress },
            claims.whenRequested(GENDER) { gender },
            claims.whenRequested(BIRTH_PLACE) { randomAddress().city },
            claims.whenRequested(ARRIVAL_DATE) { arrivalDate },
            claims.whenRequested(NATIONALITY) { nationality },
            claims.whenRequested(ISSUANCE_DATE) { iss },
            claims.whenRequested(EXPIRY_DATE) { exp },
            claims.whenRequested(ISSUING_AUTHORITY) { issuingAuthority },
            claims.whenRequested(DOCUMENT_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequested(ADMINISTRATIVE_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequested(ISSUING_COUNTRY) { issuingCountry },
            claims.whenRequested(ISSUING_JURISDICTION) { issuingJurisdiction },
        )
    }


fun OidcUserInfoExtended.buildMdlClaims(claims: Collection<String>?) =
    with(MobileDrivingLicenceDataElements) {
        val (postCode, city, state, street, locator) = addressOrRandom()
        val country = userInfo.address?.country ?: fallbackAddressCountry
        val formatted = userInfo.address?.formatted ?: formatAddress(street, locator, postCode, city)
        listOfNotNull(
            claims.whenRequested(FAMILY_NAME) { userInfo.familyName },
            claims.whenRequested(GIVEN_NAME) { userInfo.givenName },
            claims.whenRequested(BIRTH_DATE) { dateOfBirth },
            claims.whenRequested(ISSUE_DATE) { issueDate() },
            claims.whenRequested(EXPIRY_DATE) { expiryDate() },
            claims.whenRequested(ISSUING_COUNTRY) { issuingCountry },
            claims.whenRequested(ISSUING_AUTHORITY) { issuingAuthority },
            claims.whenRequested(DOCUMENT_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequested(PORTRAIT) { portrait },
            claims.whenRequested(DRIVING_PRIVILEGES) { arrayOf(fakeDrivingPrivilege()) },
            claims.whenRequested(UN_DISTINGUISHING_SIGN) { unDistinguishingSign },
            claims.whenRequested(ADMINISTRATIVE_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequested(SEX) { sex },
            claims.whenRequested(HEIGHT) { Random.nextUInt(150u, 210u) },
            claims.whenRequested(WEIGHT) { Random.nextUInt(60u, 120u) },
            claims.whenRequested(EYE_COLOUR) { randomEyeColour() },
            claims.whenRequested(HAIR_COLOUR) { randomHairColour() },
            claims.whenRequested(BIRTH_PLACE) { randomAddress().city },
            claims.whenRequested(RESIDENT_ADDRESS) { formatted },
            claims.whenRequested(PORTRAIT_CAPTURE_DATE) { portraitCaptureDate },
            claims.whenRequested(AGE_IN_YEARS) { ageInYears },
            claims.whenRequested(AGE_BIRTH_YEAR) { dateOfBirth.year.toUInt() },
            claims.whenRequested(AGE_OVER_12) { ageOver12 },
            claims.whenRequested(AGE_OVER_14) { ageOver14 },
            claims.whenRequested(AGE_OVER_16) { ageOver16 },
            claims.whenRequested(AGE_OVER_18) { ageOver18 },
            claims.whenRequested(AGE_OVER_21) { ageOver21 },
            claims.whenRequested(ISSUING_JURISDICTION) { issuingJurisdiction },
            claims.whenRequested(NATIONALITY) { nationality },
            claims.whenRequested(RESIDENT_CITY) { city },
            claims.whenRequested(RESIDENT_STATE) { state },
            claims.whenRequested(RESIDENT_POSTAL_CODE) { postCode },
            claims.whenRequested(RESIDENT_COUNTRY) { country },
            claims.whenRequested(FAMILY_NAME_NATIONAL_CHARACTER) { userInfo.familyName },
            claims.whenRequested(GIVEN_NAME_NATIONAL_CHARACTER) { userInfo.givenName },
        )
    }

fun fakeDrivingPrivilege() = DrivingPrivilege(
    vehicleCategoryCode = "B",
    issueDate = issueDate(),
    expiryDate = expiryDate(),
)

val OidcUserInfoExtended.bpk: String
    get() = getClaimAsString("urn:pvpgvat:oidc.bpk")
        ?: userInfo.subject

val OidcUserInfoExtended.dateOfBirth
    get() = userInfo.birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: randomDateOfBirth()

val OidcUserInfoExtended.email
    get() = userInfo.email
        ?: "info@example.com"

val OidcUserInfoExtended.phoneNumber
    get() = userInfo.phoneNumber
        ?: "+49-89-99998-001"

val OidcUserInfoExtended.sex
    get() = getClaimAsString("urn:eidgvat:attributes.gender")?.toIsoSexEnum()
        ?: IsoSexEnum.NOT_KNOWN

val OidcUserInfoExtended.genderText
    get() = getClaimAsString("urn:eidgvat:attributes.gender")
        ?: "unknown"

val OidcUserInfoExtended.gender
    get() = getClaimAsString("urn:eidgvat:attributes.gender")?.toIsoGenderEnum()
        ?: IsoIec5218Gender.NOT_KNOWN

fun String.toIsoSexEnum() = when (this) {
    "W" -> IsoSexEnum.FEMALE
    "M" -> IsoSexEnum.MALE
    else -> IsoSexEnum.NOT_KNOWN
}

fun String.toIsoGenderEnum() = when (this) {
    "W" -> IsoIec5218Gender.FEMALE
    "M" -> IsoIec5218Gender.MALE
    else -> IsoIec5218Gender.NOT_KNOWN
}

val OidcUserInfoExtended.ageOver12
    get() = getClaimAsString("org.iso.18013.5.1:age_over_12")?.toBoolean()
        ?: ageOver14

val OidcUserInfoExtended.ageOver14
    get() = getClaimAsString("org.iso.18013.5.1:age_over_14")?.toBoolean()
        ?: ageOver16

val OidcUserInfoExtended.ageOver16
    get() = getClaimAsString("org.iso.18013.5.1:age_over_16")?.toBoolean()
        ?: ageOver18

val OidcUserInfoExtended.ageOver18: Boolean
    get() = userInfo.ageOver18
        ?: getClaimAsString("org.iso.18013.5.1:age_over_18")?.toBoolean()
        ?: ageOver21

fun Instant.toLocalDate() = toLocalDateTime(TimeZone.currentSystemDefault()).date

val OidcUserInfoExtended.ageOver21: Boolean
    get() = getClaimAsString("org.iso.18013.5.1:age_over_21")?.toBoolean()
        ?: (dateOfBirth < Clock.System.now().toLocalDate().minus(DatePeriod(21)))

val OidcUserInfoExtended.ageInYears: UInt
    get() = (Clock.System.now().toLocalDate().minus(dateOfBirth)).years.toUInt()

val OidcUserInfoExtended.portrait: ByteArray?
    get() = userInfo.picture?.decodeToByteArray(Base64())
        ?: getClaimAsString("org.iso.18013.5.1:portrait")?.decodeToByteArray(Base64())

val OidcUserInfoExtended.portraitCaptureDate: LocalDate?
    get() = getClaimAsString("org.iso.18013.5.1:portrait_capture_date")
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

val OidcUserInfoExtended.mainAddress: String?
    get() = userInfo.address?.formatted
        ?: getClaimAsString("urn:eidgvat:attributes.mainAddress")

val OidcUserInfoExtended.arrivalDate: LocalDate
    get() = getClaimAsString("urn:eidgvat:attributes.mainAddressRegistrationDate")
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate(2000, Random.nextInt(1, 12), Random.nextInt(1, 28))

val OidcUserInfoExtended.nationality: String
    get() = getClaimAsString("urn:eidgvat:attributes.nationality")?.let {
        runCatching {
            Json.parseToJsonElement(it).jsonArray.first().jsonPrimitive.content
        }.getOrNull()
            ?.mapToAlpha2()
    } ?: "AT"

fun String.mapToAlpha2() = when (this) {
    "AUT" -> "AT"
    "DEU" -> "DE"
    "CHE" -> "CH"
    else -> "XX"
}

val OidcUserInfoExtended.legalName: String
    get() = getClaimAsString("urn:pvpgvat:oidc.mandator_legal_person_full_name")
        ?: "A-SIT Plus GmbH"

val OidcUserInfoExtended.legalPersonIdentifier: String
    get() = getClaimAsString("urn:pvpgvat:oidc.mandator_legal_person_source_pin")
        ?: "XFN+436920f"

fun OidcUserInfoExtended.getClaimAsString(key: String): String? {
    val element = jsonObject[key]
    if (element is JsonPrimitive) {
        return element.content
    }
    return element?.toString()
}

fun OidcUserInfoExtended.getClaims(vararg key: String): String? {
    return key.mapNotNull { getClaimAsString(it) }.ifEmpty { null }?.joinToString(" ")
}

private fun formatAddress(street: String, locator: Int, postalCode: String, city: String) =
    "$street $locator, $postalCode $city"

private fun Collection<String>?.whenRequested(key: String, value: () -> Any?): ClaimToBeIssued? =
    if (isNullOrContains(key)) value()?.let { ClaimToBeIssued(key, it.encodeIfNeeded()) } else null

private fun claim(key: String, value: () -> Any?): ClaimToBeIssued? =
    value()?.let { ClaimToBeIssued(key, it.encodeIfNeeded()) }

private fun Collection<String>?.whenRequested(key: String, selectivelyDisclosable: Boolean, value: () -> Any?): ClaimToBeIssued? =
    if (isNullOrContains(key)) value()?.let { ClaimToBeIssued(key, it.encodeIfNeeded(), selectivelyDisclosable) } else null


// TODO when PR#160 will be used, replace with whenRequested
private fun Collection<String>?.whenRequestedIssueIsoName(sdJwtName: String, value: () -> Any?): ClaimToBeIssued? {
    val attrName = EuPidScheme.mapIsoToSdJwtAttributes.entries.firstOrNull { it.value == sdJwtName }?.key
        ?: sdJwtName
    return if (isNullOrContains(sdJwtName)) {
        value()?.let { ClaimToBeIssued(attrName, it.encodeIfNeeded()) }
    } else if (attrName != sdJwtName && isNullOrContains(attrName)) {
        value()?.let { ClaimToBeIssued(attrName, it.encodeIfNeeded()) }
    } else {
        null
    }
}

/**
 * Wallet App may request SD-JWT claim names, but we want to issue the ISO claim names
 */
private fun Collection<String>?.whenRequestedOrAltName(key: String, value: () -> Any?): ClaimToBeIssued? =
    if (isNullOrContains(key)) value()?.let { ClaimToBeIssued(key, it.encodeIfNeeded()) } else
        EuPidScheme.mapIsoToSdJwtAttributes[key]?.let { newKey ->
            if (isNullOrContains(newKey)) value()?.let {
                ClaimToBeIssued(key, it.encodeIfNeeded())
            } else {
                null
            }
        }

fun Collection<String>?.isNullOrContains(name: String) =
    this == null || contains(name)

@OptIn(ExperimentalEncodingApi::class)
fun Any.encodeIfNeeded() = if (this is ByteArray) kotlin.io.encoding.Base64.encode(this) else this

private fun expiryDate() = LocalDate.parse("2025-12-31")

private fun issueDate() = LocalDate.parse("2023-01-01")

private val issuingCountry = "AT"
private val issuingJurisdiction = "AT-0"
private val issuingAuthority = "Miniwahr"
private val unDistinguishingSign = "A"
private val fallbackBirthCountry = "AT"
private val fallbackAddressCountry = "AT"

private fun String.toState(): String = when {
    this.startsWith("1") -> "Wien"
    this.startsWith("2") -> "Niederösterreich"
    this.startsWith("3") -> "Niederösterreich"
    this.startsWith("4") -> "Oberösterreich"
    this.startsWith("5") -> "Salzburg"
    this.startsWith("6") -> "Tirol"
    this.startsWith("7") -> "Burgenland"
    this.startsWith("8") -> "Steiermark"
    this.startsWith("9") -> "Kärnten"
    else -> "Österreich"
}

private fun randomEyeColour() =
    listOf("black", "blue", "brown", "dichromatic", "grey", "green", "hazel", "maroon", "pink", "unknown").random()

private fun randomHairColour() =
    listOf("bald", "black", "blond", "brown", "grey", "red", "auburn", "sandy", "white", "unknown").random()

data class Address(val postCode: String, val city: String, val state: String, val street: String, val locator: Int)

private fun randomAddress(): Address =
    listOf(
        Address("6900", "Bregenz", "Vorarlberg", randomStreet(), randomAddressLocator()),
        Address("6010", "Innsbruck", "Tirol", randomStreet(), randomAddressLocator()),
        Address("5010", "Salzburg", "Salzburg", randomStreet(), randomAddressLocator()),
        Address("4020", "Linz", "Oberösterreich", randomStreet(), randomAddressLocator()),
        Address("3100", "St. Pölten", "Niederösterreich", randomStreet(), randomAddressLocator()),
        Address("1010", "Wien", "Wien", randomStreet(), randomAddressLocator()),
        Address("8010", "Graz", "Steiermark", randomStreet(), randomAddressLocator()),
        Address("7000", "Eisenstadt", "Burgenland", randomStreet(), randomAddressLocator()),
        Address("9020", "Klagenfurt", "Kärnten", randomStreet(), randomAddressLocator())
    ).random()

private fun randomAddressLocator() = Random.nextInt(1, 99)

private fun randomStreet() =
    listOf("Hauptstraße", "Herrengasse", "Hauptplatz", "Landstraße", "Dorfstraße").random()

private fun randomDateOfBirth() = LocalDate(Random.nextInt(1970, 2000), Random.nextInt(1, 12), Random.nextInt(1, 28))
