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
    userInfo: OidcUserInfoExtended,
    iss: Instant,
    exp: Instant,
    loader: EPrescriptionLoader,
): List<ClaimToBeIssued> =
    when (this) {
        is IdAustriaScheme -> userInfo.buildIdaClaims()
        is EuPidScheme -> /* // TODO Use this once ARF PR#160 is through
        if (representation == CredentialRepresentation.SD_JWT)
            userInfo.buildEupidClaimsSdJwt(claims, iss, exp)
        else*/
            userInfo.buildEupidClaims(iss, exp)

        is HealthIdScheme -> userInfo.buildHealthIdClaims(iss, exp, loader)
        is TaxIdScheme -> userInfo.buildTaxIdClaims(iss, exp)
        is MobileDrivingLicenceScheme -> userInfo.buildMdlClaims()
        is PowerOfRepresentationScheme -> userInfo.buildPorClaims(iss, exp)
        is CertificateOfResidenceScheme -> userInfo.buildCorClaims(iss, exp)
        is CompanyRegistrationScheme -> userInfo.buildCompanyRegistrationClaims()
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

fun OidcUserInfoExtended.buildIdaClaims() =
    with(IdAustriaScheme.Attributes) {
        listOfNotNull(
            claim(BPK) { bpk },
            claim(FIRSTNAME) { userInfo.givenName },
            claim(LASTNAME) { userInfo.familyName },
            claim(DATE_OF_BIRTH) { dateOfBirth },
            claim(PORTRAIT) { portrait },
            claim(MAIN_ADDRESS) { mainAddress },
            claim(AGE_OVER_14) { ageOver14 },
            claim(AGE_OVER_16) { ageOver16 },
            claim(AGE_OVER_18) { ageOver18 },
            claim(AGE_OVER_21) { ageOver21 },
            claim(GENDER) { genderText },
        )
    }

fun OidcUserInfoExtended.buildEupidClaimsSdJwt(iss: Instant, exp: Instant) =
    with(EuPidScheme.SdJwtAttributes) {
        val (postCode, city, state, street, locator) = addressOrRandom()
        val (_, ourBirthCity, ourBirthState, ourBirthStreet) = randomAddress()
        val country = userInfo.address?.country ?: fallbackAddressCountry
        val formatted = userInfo.address?.formatted ?: formatAddress(street, locator, postCode, city)
        listOfNotNull(
            claim(FAMILY_NAME) { userInfo.familyName },
            claim(GIVEN_NAME) { userInfo.givenName },
            claim(BIRTH_DATE) { dateOfBirth },
            claim(PORTRAIT) { portrait },
            claim(PREFIX_AGE_EQUAL_OR_OVER) {
                with(EuPidScheme.SdJwtAttributes.AgeEqualOrOver) {
                    listOf(
                        claim(EQUAL_OR_OVER_12) { ageOver12 },
                        claim(EQUAL_OR_OVER_14) { ageOver14 },
                        claim(EQUAL_OR_OVER_16) { ageOver16 },
                        claim(EQUAL_OR_OVER_18) { ageOver18 },
                        claim(EQUAL_OR_OVER_21) { ageOver21 },
                    )
                }
            },
            claim(AGE_EQUAL_OR_OVER_12) { ageOver12 },
            claim(AGE_EQUAL_OR_OVER_14) { ageOver14 },
            claim(AGE_EQUAL_OR_OVER_16) { ageOver16 },
            claim(AGE_EQUAL_OR_OVER_18) { ageOver18 },
            claim(AGE_EQUAL_OR_OVER_21) { ageOver21 },
            claim(AGE_IN_YEARS) { ageInYears },
            claim(AGE_BIRTH_YEAR) { dateOfBirth.year.toUInt() },
            claim(FAMILY_NAME_BIRTH) { userInfo.familyName },
            claim(GIVEN_NAME_BIRTH) { userInfo.givenName },
            claim(PREFIX_PLACE_OF_BIRTH) {
                with(EuPidScheme.SdJwtAttributes.PlaceOfBirth) {
                    listOf(
                        claim(LOCALITY) { ourBirthCity }
                    )
                }
            },
            claim(BIRTH_PLACE) { ourBirthCity },
            claim(PLACE_OF_BIRTH_LOCALITY) { ourBirthCity },
            claim(PREFIX_PLACE_OF_BIRTH) {
                with(EuPidScheme.SdJwtAttributes.Address) {
                    listOf(
                        claim(FORMATTED) { formatted },
                        claim(COUNTRY) { country },
                        claim(REGION) { state },
                        claim(LOCALITY) { city },
                        claim(POSTAL_CODE) { postCode },
                        claim(STREET) { street },
                        claim(HOUSE_NUMBER) { locator }.toString(),
                    )
                }
            },
            claim(ADDRESS_FORMATTED) { formatted },
            claim(ADDRESS_COUNTRY) { country },
            claim(ADDRESS_REGION) { state },
            claim(ADDRESS_LOCALITY) { city },
            claim(ADDRESS_POSTAL_CODE) { postCode },
            claim(ADDRESS_STREET) { street },
            claim(ADDRESS_HOUSE_NUMBER) { locator.toString() },
            claim(GENDER) { genderText },
            claim(NATIONALITIES) { setOf(nationality) },
            claim(ISSUANCE_DATE) { iss },
            claim(EXPIRY_DATE) { exp },
            claim(ISSUING_AUTHORITY) { issuingAuthority },
            claim(DOCUMENT_NUMBER) { UUID.randomUUID().toString() },
            claim(ISSUING_COUNTRY) { issuingCountry },
            claim(ISSUING_JURISDICTION) { issuingJurisdiction },
            claim(PERSONAL_ADMINISTRATIVE_NUMBER) { UUID.randomUUID().toString() },
            claim(EMAIL_ADDRESS) { email },
            claim(MOBILE_PHONE_NUMBER) { phoneNumber },
        )
    }

fun OidcUserInfoExtended.buildEupidClaims(iss: Instant, exp: Instant) =
    with(EuPidScheme.Attributes) {
        val (postCode, city, state, street, locator) = addressOrRandom()
        val (_, ourBirthCity, ourBirthState, ourBirthStreet) = randomAddress()
        val country = userInfo.address?.country ?: fallbackAddressCountry
        val formatted = userInfo.address?.formatted ?: formatAddress(street, locator, postCode, city)
        listOfNotNull(
            claim(FAMILY_NAME) { userInfo.familyName },
            claim(GIVEN_NAME) { userInfo.givenName },
            claim(BIRTH_DATE) { dateOfBirth },
            claim(BIRTH_PLACE) { ourBirthCity },
            claim(NATIONALITY) { setOf(nationality) },
            claim(RESIDENT_ADDRESS) { formatted },
            claim(RESIDENT_COUNTRY) { country },
            claim(RESIDENT_STATE) { state },
            claim(RESIDENT_CITY) { city },
            claim(RESIDENT_POSTAL_CODE) { postCode },
            claim(RESIDENT_STREET) { street },
            claim(RESIDENT_HOUSE_NUMBER) { locator.toString() },
            claim(PERSONAL_ADMINISTRATIVE_NUMBER) { UUID.randomUUID().toString() },
            claim(PORTRAIT) { portrait },
            claim(FAMILY_NAME_BIRTH) { userInfo.familyName },
            claim(GIVEN_NAME_BIRTH) { userInfo.givenName },
            claim(SEX) { gender.code },
            claim(EMAIL_ADDRESS) { email },
            claim(MOBILE_PHONE_NUMBER) { phoneNumber },
            claim(EXPIRY_DATE) { exp },
            claim(ISSUING_AUTHORITY) { issuingAuthority },
            claim(ISSUING_COUNTRY) { issuingCountry },
            claim(DOCUMENT_NUMBER) { UUID.randomUUID().toString() },
            claim(ISSUING_JURISDICTION) { issuingJurisdiction },
            claim(ISSUANCE_DATE) { iss },
            claim(AGE_OVER_12) { ageOver12 },
            claim(AGE_OVER_14) { ageOver14 },
            claim(AGE_OVER_16) { ageOver16 },
            claim(AGE_OVER_18) { ageOver18 },
            claim(AGE_OVER_21) { ageOver21 },
            claim(AGE_IN_YEARS) { ageInYears },
            claim(AGE_BIRTH_YEAR) { dateOfBirth.year.toUInt() },
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

fun OidcUserInfoExtended.buildPorClaims(iss: Instant, exp: Instant) =
    with(PowerOfRepresentationDataElements) {
        listOfNotNull(
            claim(LEGAL_PERSON_IDENTIFIER, false) { legalPersonIdentifier },
            claim(LEGAL_NAME, false) { legalName },
            claim(FULL_POWERS, false) { true },
            //claim(E_SERVICE, false) { eService },
            claim(EFFECTIVE_FROM_DATE, false) { iss },
            claim(EFFECTIVE_UNTIL_DATE, false) { exp },
            claim(ISSUANCE_DATE, false) { iss },
            claim(EXPIRY_DATE, false) { exp },
            claim(ISSUING_AUTHORITY, false) { issuingAuthority },
            claim(ISSUING_COUNTRY, false) { issuingCountry },
            claim(ISSUING_JURISDICTION, false) { issuingJurisdiction },
            claim(DOCUMENT_NUMBER, false) { UUID.randomUUID().toString() },
            claim(ADMINISTRATIVE_NUMBER, false) { UUID.randomUUID().toString() },
        )
    }

fun OidcUserInfoExtended.buildTaxIdClaims(iss: Instant, exp: Instant) =
    with(TaxIdScheme.Attributes) {
        listOfNotNull(
            claim(TAX_NUMBER, false) { "ATU12345678" },
            claim(AFFILIATION_COUNTRY, false) { "AT" },
            claim(REGISTERED_GIVEN_NAME, false) { userInfo.givenName },
            claim(REGISTERED_FAMILY_NAME, false) { userInfo.familyName },
            //claim(E_SERVICE, false) { eService },
            claim(RESIDENT_ADDRESS, false) { addressOrRandom().let { it.street + " " + it.locator + ", " + it.postCode + " " + it.city } },
            claim(BIRTH_DATE, false) { dateOfBirth },
            claim(CHURCH_TAX_ID, false) { "ATU13339991" },
            claim(IBAN, false) { "AT023200051286875134" },
            claim(PID_ID, false) { "PID12345678" },
            claim(ISSUANCE_DATE, false) { iss },
            claim(EXPIRY_DATE, false) { exp },
            claim(ISSUING_AUTHORITY, false) { issuingAuthority },
            claim(ISSUING_COUNTRY, false) { issuingCountry },
            claim(ISSUING_JURISDICTION, false) { issuingJurisdiction },
            claim(DOCUMENT_NUMBER, false) { UUID.randomUUID().toString() },
            claim(ADMINISTRATIVE_NUMBER, false) { UUID.randomUUID().toString() },
        )
    }

fun OidcUserInfoExtended.buildHealthIdClaims(iss: Instant, exp: Instant, loader: EPrescriptionLoader) =
    with(HealthIdScheme.Attributes) {
        val ottElement =
            loader.load(bpk, userInfo.givenName!!, userInfo.familyName!!, userInfo.birthDate!!).getOrNull()?.data
                ?: throw IllegalArgumentException("No data from EPrescriptionLoader")
        listOfNotNull(
            claim(ONE_TIME_TOKEN, false) { ottElement.oneTimeToken },
            claim(AFFILIATION_COUNTRY, false) { ottElement.countryCode },
            claim(ISSUE_DATE, false) { iss },
            claim(EXPIRY_DATE, false) { exp },
            claim(ISSUING_AUTHORITY, false) { issuingAuthority },
            claim(ISSUING_COUNTRY, false) { issuingCountry },
            claim(ISSUING_JURISDICTION, false) { issuingJurisdiction },
            claim(DOCUMENT_NUMBER, false) { UUID.randomUUID().toString() },
            claim(ADMINISTRATIVE_NUMBER, false) { UUID.randomUUID().toString() },
        )
    }


fun OidcUserInfoExtended.buildCompanyRegistrationClaims() =
    with(CompanyRegistrationDataElements) {
        listOfNotNull(
            claim(COMPANY_NAME) { legalName },
            claim(COMPANY_TYPE) { "Einzelunternehmen" },
            claim(COMPANY_STATUS) { "economically active" },
            claim(COMPANY_ACTIVITY) {
                with(CompanyRegistrationDataElements.CompanyActivity) {
                    listOf(
                        claim(NACE_CODE) { "J62" },
                        //claim(ACTIVITY_DESCRIPTION){"7500"}
                    )
                }
            },
            claim(REGISTRATION_DATE) { LocalDate(2015, 6, 25) },
            //claim(COMPANY_END_DATE) { LocalDate(2025, Random.nextInt(1, 12), Random.nextInt(1, 28)) },
            claim(COMPANY_EUID) { "ATCHCUSP.69743824" },
            claim(VAT_NUMBER) { "ATU69743824" },
            claim(COMPANY_CONTACT_DATA) {
                with(CompanyRegistrationDataElements.ContactData) {
                    listOf(
                        claim(EMAIL) { "office@a-sit.at" },
                        claim(TELEPHONE) { "+43-555-${Random.nextInt(1, 9999)}" }
                    )
                }
            },
            claim(REGISTERED_ADDRESS) {
                with(CompanyRegistrationDataElements.Address) {
                    listOf(
                        claim(THOROUGHFARE) { "Seidlgasse" },
                        claim(LOCATOR_DESIGNATOR) { "22/9" },
                        claim(POST_CODE) { "1030" },
                        claim(POST_NAME) { "Wien" },
                        claim(ADMIN_UNIT_L_1) { "AT" },
                        claim(ADMIN_UNIT_L_2) { "Wien" }
                    )
                }
            },
        )
    }

fun OidcUserInfoExtended.buildCorClaims(iss: Instant, exp: Instant) =
    with(CertificateOfResidenceDataElements) {
        val (postCode, city, state, street, locator) = addressOrRandom()
        val country = userInfo.address?.country ?: fallbackAddressCountry
        val fullAddress = formatAddress(street, locator, postCode, city)
        listOfNotNull(
            claim(FAMILY_NAME) { userInfo.familyName },
            claim(GIVEN_NAME) { userInfo.givenName },
            claim(BIRTH_DATE) { dateOfBirth },
            claim(RESIDENCE_ADDRESS) {
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
            claim(RESIDENCE_ADDRESS_THOROUGHFARE) { street },
            claim(RESIDENCE_ADDRESS_LOCATOR_DESIGNATOR) { locator },
            claim(RESIDENCE_ADDRESS_POST_CODE) { postCode },
            claim(RESIDENCE_ADDRESS_POST_NAME) { city },
            claim(RESIDENCE_ADDRESS_ADMIN_UNIT_L_1) { country },
            claim(RESIDENCE_ADDRESS_ADMIN_UNIT_L_2) { state },
            claim(RESIDENCE_ADDRESS_FULL_ADDRESS) { fullAddress },
            claim(GENDER) { gender },
            claim(BIRTH_PLACE) { randomAddress().city },
            claim(ARRIVAL_DATE) { arrivalDate },
            claim(NATIONALITY) { nationality },
            claim(ISSUANCE_DATE) { iss },
            claim(EXPIRY_DATE) { exp },
            claim(ISSUING_AUTHORITY) { issuingAuthority },
            claim(DOCUMENT_NUMBER) { UUID.randomUUID().toString() },
            claim(ADMINISTRATIVE_NUMBER) { UUID.randomUUID().toString() },
            claim(ISSUING_COUNTRY) { issuingCountry },
            claim(ISSUING_JURISDICTION) { issuingJurisdiction },
        )
    }


fun OidcUserInfoExtended.buildMdlClaims() =
    with(MobileDrivingLicenceDataElements) {
        val (postCode, city, state, street, locator) = addressOrRandom()
        val country = userInfo.address?.country ?: fallbackAddressCountry
        val formatted = userInfo.address?.formatted ?: formatAddress(street, locator, postCode, city)
        listOfNotNull(
            claim(FAMILY_NAME) { userInfo.familyName },
            claim(GIVEN_NAME) { userInfo.givenName },
            claim(BIRTH_DATE) { dateOfBirth },
            claim(ISSUE_DATE) { issueDate() },
            claim(EXPIRY_DATE) { expiryDate() },
            claim(ISSUING_COUNTRY) { issuingCountry },
            claim(ISSUING_AUTHORITY) { issuingAuthority },
            claim(DOCUMENT_NUMBER) { UUID.randomUUID().toString() },
            claim(PORTRAIT) { portrait },
            claim(DRIVING_PRIVILEGES) { arrayOf(fakeDrivingPrivilege()) },
            claim(UN_DISTINGUISHING_SIGN) { unDistinguishingSign },
            claim(ADMINISTRATIVE_NUMBER) { UUID.randomUUID().toString() },
            claim(SEX) { sex },
            claim(HEIGHT) { Random.nextUInt(150u, 210u) },
            claim(WEIGHT) { Random.nextUInt(60u, 120u) },
            claim(EYE_COLOUR) { randomEyeColour() },
            claim(HAIR_COLOUR) { randomHairColour() },
            claim(BIRTH_PLACE) { randomAddress().city },
            claim(RESIDENT_ADDRESS) { formatted },
            claim(PORTRAIT_CAPTURE_DATE) { portraitCaptureDate },
            claim(AGE_IN_YEARS) { ageInYears },
            claim(AGE_BIRTH_YEAR) { dateOfBirth.year.toUInt() },
            claim(AGE_OVER_12) { ageOver12 },
            claim(AGE_OVER_14) { ageOver14 },
            claim(AGE_OVER_16) { ageOver16 },
            claim(AGE_OVER_18) { ageOver18 },
            claim(AGE_OVER_21) { ageOver21 },
            claim(ISSUING_JURISDICTION) { issuingJurisdiction },
            claim(NATIONALITY) { nationality },
            claim(RESIDENT_CITY) { city },
            claim(RESIDENT_STATE) { state },
            claim(RESIDENT_POSTAL_CODE) { postCode },
            claim(RESIDENT_COUNTRY) { country },
            claim(FAMILY_NAME_NATIONAL_CHARACTER) { userInfo.familyName },
            claim(GIVEN_NAME_NATIONAL_CHARACTER) { userInfo.givenName },
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

private fun formatAddress(street: String, locator: Int, postalCode: String, city: String) =
    "$street $locator, $postalCode $city"

private fun claim(key: String, value: () -> Any?): ClaimToBeIssued? =
    value()?.let { ClaimToBeIssued(key, it.encodeIfNeeded()) }

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
