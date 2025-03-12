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
        is IdAustriaScheme -> userInfo.buildIdaClaims(this.useSd())
        is EuPidScheme -> /* // TODO Use this once ARF PR#160 is through
        if (representation == CredentialRepresentation.SD_JWT)
            userInfo.buildEupidClaimsSdJwt(claims, iss, exp, this.useSd())
        else*/
            userInfo.buildEupidClaims(iss, exp, this.useSd())

        is HealthIdScheme -> userInfo.buildHealthIdClaims(iss, exp, loader, this.useSd())
        is TaxIdScheme -> userInfo.buildTaxIdClaims(iss, exp, this.useSd())
        is MobileDrivingLicenceScheme -> userInfo.buildMdlClaims(this.useSd())
        is PowerOfRepresentationScheme -> userInfo.buildPorClaims(iss, exp, this.useSd())
        is CertificateOfResidenceScheme -> userInfo.buildCorClaims(iss, exp, this.useSd())
        is CompanyRegistrationScheme -> userInfo.buildCompanyRegistrationClaims(this.useSd())
        else -> TODO("$this is not implemented in buildClaims()")
    }.also { Napier.v("${this}.buildClaims returns $it") }

fun ConstantIndex.CredentialScheme.useSd() = when (this) {
    is HealthIdScheme -> false
    is TaxIdScheme -> false
    is PowerOfRepresentationScheme -> false
    is CompanyRegistrationScheme -> false
    else -> true
}

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

fun OidcUserInfoExtended.buildIdaClaims(useSd: Boolean) =
    with(IdAustriaScheme.Attributes) {
        listOfNotNull(
            claim(BPK, useSd) { bpk },
            claim(FIRSTNAME, useSd) { userInfo.givenName },
            claim(LASTNAME, useSd) { userInfo.familyName },
            claim(DATE_OF_BIRTH, useSd) { dateOfBirth },
            claim(PORTRAIT, useSd) { portrait },
            claim(MAIN_ADDRESS, useSd) { mainAddress },
            claim(AGE_OVER_14, useSd) { ageOver14 },
            claim(AGE_OVER_16, useSd) { ageOver16 },
            claim(AGE_OVER_18, useSd) { ageOver18 },
            claim(AGE_OVER_21, useSd) { ageOver21 },
            claim(GENDER, useSd) { genderText },
        )
    }

fun OidcUserInfoExtended.buildEupidClaimsSdJwt(iss: Instant, exp: Instant, useSd: Boolean) =
    with(EuPidScheme.SdJwtAttributes) {
        val (postCode, city, state, street, locator) = addressOrRandom()
        val (_, ourBirthCity, ourBirthState, ourBirthStreet) = randomAddress()
        val country = userInfo.address?.country ?: fallbackAddressCountry
        val formatted = userInfo.address?.formatted ?: formatAddress(street, locator, postCode, city)
        listOfNotNull(
            claim(FAMILY_NAME, useSd) { userInfo.familyName },
            claim(GIVEN_NAME, useSd) { userInfo.givenName },
            claim(BIRTH_DATE, useSd) { dateOfBirth },
            claim(PORTRAIT, useSd) { portrait },
            claim(PREFIX_AGE_EQUAL_OR_OVER, useSd) {
                with(EuPidScheme.SdJwtAttributes.AgeEqualOrOver) {
                    listOf(
                        claim(EQUAL_OR_OVER_12, useSd) { ageOver12 },
                        claim(EQUAL_OR_OVER_14, useSd) { ageOver14 },
                        claim(EQUAL_OR_OVER_16, useSd) { ageOver16 },
                        claim(EQUAL_OR_OVER_18, useSd) { ageOver18 },
                        claim(EQUAL_OR_OVER_21, useSd) { ageOver21 },
                    )
                }
            },
            claim(AGE_EQUAL_OR_OVER_12, useSd) { ageOver12 },
            claim(AGE_EQUAL_OR_OVER_14, useSd) { ageOver14 },
            claim(AGE_EQUAL_OR_OVER_16, useSd) { ageOver16 },
            claim(AGE_EQUAL_OR_OVER_18, useSd) { ageOver18 },
            claim(AGE_EQUAL_OR_OVER_21, useSd) { ageOver21 },
            claim(AGE_IN_YEARS, useSd) { ageInYears },
            claim(AGE_BIRTH_YEAR, useSd) { dateOfBirth.year.toUInt() },
            claim(FAMILY_NAME_BIRTH, useSd) { userInfo.familyName },
            claim(GIVEN_NAME_BIRTH, useSd) { userInfo.givenName },
            claim(PREFIX_PLACE_OF_BIRTH, useSd) {
                with(EuPidScheme.SdJwtAttributes.PlaceOfBirth) {
                    listOf(
                        claim(LOCALITY, useSd) { ourBirthCity }
                    )
                }
            },
            claim(BIRTH_PLACE, useSd) { ourBirthCity },
            claim(PLACE_OF_BIRTH_LOCALITY, useSd) { ourBirthCity },
            claim(PREFIX_PLACE_OF_BIRTH, useSd) {
                with(EuPidScheme.SdJwtAttributes.Address) {
                    listOf(
                        claim(FORMATTED, useSd) { formatted },
                        claim(COUNTRY, useSd) { country },
                        claim(REGION, useSd) { state },
                        claim(LOCALITY, useSd) { city },
                        claim(POSTAL_CODE, useSd) { postCode },
                        claim(STREET, useSd) { street },
                        claim(HOUSE_NUMBER, useSd) { locator }.toString(),
                    )
                }
            },
            claim(ADDRESS_FORMATTED, useSd) { formatted },
            claim(ADDRESS_COUNTRY, useSd) { country },
            claim(ADDRESS_REGION, useSd) { state },
            claim(ADDRESS_LOCALITY, useSd) { city },
            claim(ADDRESS_POSTAL_CODE, useSd) { postCode },
            claim(ADDRESS_STREET, useSd) { street },
            claim(ADDRESS_HOUSE_NUMBER, useSd) { locator.toString() },
            claim(GENDER, useSd) { genderText },
            claim(NATIONALITIES, useSd) { setOf(nationality) },
            claim(ISSUANCE_DATE, useSd) { iss },
            claim(EXPIRY_DATE, useSd) { exp },
            claim(ISSUING_AUTHORITY, useSd) { issuingAuthority },
            claim(DOCUMENT_NUMBER, useSd) { UUID.randomUUID().toString() },
            claim(ISSUING_COUNTRY, useSd) { issuingCountry },
            claim(ISSUING_JURISDICTION, useSd) { issuingJurisdiction },
            claim(PERSONAL_ADMINISTRATIVE_NUMBER, useSd) { UUID.randomUUID().toString() },
            claim(EMAIL_ADDRESS, useSd) { email },
            claim(MOBILE_PHONE_NUMBER, useSd) { phoneNumber },
        )
    }

fun OidcUserInfoExtended.buildEupidClaims(iss: Instant, exp: Instant, useSd: Boolean) =
    with(EuPidScheme.Attributes) {
        val (postCode, city, state, street, locator) = addressOrRandom()
        val (_, ourBirthCity, ourBirthState, ourBirthStreet) = randomAddress()
        val country = userInfo.address?.country ?: fallbackAddressCountry
        val formatted = userInfo.address?.formatted ?: formatAddress(street, locator, postCode, city)
        listOfNotNull(
            claim(FAMILY_NAME, useSd) { userInfo.familyName },
            claim(GIVEN_NAME, useSd) { userInfo.givenName },
            claim(BIRTH_DATE, useSd) { dateOfBirth },
            claim(BIRTH_PLACE, useSd) { ourBirthCity },
            claim(NATIONALITY, useSd) { setOf(nationality) },
            claim(RESIDENT_ADDRESS, useSd) { formatted },
            claim(RESIDENT_COUNTRY, useSd) { country },
            claim(RESIDENT_STATE, useSd) { state },
            claim(RESIDENT_CITY, useSd) { city },
            claim(RESIDENT_POSTAL_CODE, useSd) { postCode },
            claim(RESIDENT_STREET, useSd) { street },
            claim(RESIDENT_HOUSE_NUMBER, useSd) { locator.toString() },
            claim(PERSONAL_ADMINISTRATIVE_NUMBER, useSd) { UUID.randomUUID().toString() },
            claim(PORTRAIT, useSd) { portrait },
            claim(FAMILY_NAME_BIRTH, useSd) { userInfo.familyName },
            claim(GIVEN_NAME_BIRTH, useSd) { userInfo.givenName },
            claim(SEX, useSd) { gender.code },
            claim(EMAIL_ADDRESS, useSd) { email },
            claim(MOBILE_PHONE_NUMBER, useSd) { phoneNumber },
            claim(EXPIRY_DATE, useSd) { exp },
            claim(ISSUING_AUTHORITY, useSd) { issuingAuthority },
            claim(ISSUING_COUNTRY, useSd) { issuingCountry },
            claim(DOCUMENT_NUMBER, useSd) { UUID.randomUUID().toString() },
            claim(ISSUING_JURISDICTION, useSd) { issuingJurisdiction },
            claim(ISSUANCE_DATE, useSd) { iss },
            claim(AGE_OVER_12, useSd) { ageOver12 },
            claim(AGE_OVER_14, useSd) { ageOver14 },
            claim(AGE_OVER_16, useSd) { ageOver16 },
            claim(AGE_OVER_18, useSd) { ageOver18 },
            claim(AGE_OVER_21, useSd) { ageOver21 },
            claim(AGE_IN_YEARS, useSd) { ageInYears },
            claim(AGE_BIRTH_YEAR, useSd) { dateOfBirth.year.toUInt() },
        )
    }

private fun OidcUserInfoExtended.addressOrRandom(): Address = userInfo.address?.let {
    if (it.postalCode != null && it.locality != null && it.region != null && it.street != null) {
        Address(
            postCode = it.postalCode!!,
            city = it.locality!!,
            state = it.region!!,
            street = it.street!!.substringBefore(" "),
            locator = it.street!!.substringAfter(" ").toIntOrNull() ?: randomAddressLocator()
        )
    } else null
} ?: getClaimAsString("urn:eidgvat:attributes.mainAddress")?.let { idaAddress ->
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

private fun JsonObject?.getPrimitiveContent(key: String) = (this?.get(key) as? JsonPrimitive)?.content

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
            claim(DOCUMENT_NUMBER, useSd) { UUID.randomUUID().toString() },
            claim(ADMINISTRATIVE_NUMBER, useSd) { UUID.randomUUID().toString() },
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
                addressOrRandom()
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
            claim(DOCUMENT_NUMBER, useSd) { UUID.randomUUID().toString() },
            claim(ADMINISTRATIVE_NUMBER, useSd) { UUID.randomUUID().toString() },
        )
    }

fun OidcUserInfoExtended.buildHealthIdClaims(iss: Instant, exp: Instant, loader: EPrescriptionLoader, useSd: Boolean) =
    with(HealthIdScheme.Attributes) {
        val ottElement =
            loader.load(bpk, userInfo.givenName!!, userInfo.familyName!!, userInfo.birthDate!!).getOrNull()?.data
                ?: throw IllegalArgumentException("No data from EPrescriptionLoader")
        listOfNotNull(
            claim(ONE_TIME_TOKEN, useSd) { ottElement.oneTimeToken },
            claim(AFFILIATION_COUNTRY, useSd) { ottElement.countryCode },
            claim(ISSUE_DATE, useSd) { iss },
            claim(EXPIRY_DATE, useSd) { exp },
            claim(ISSUING_AUTHORITY, useSd) { issuingAuthority },
            claim(ISSUING_COUNTRY, useSd) { issuingCountry },
            claim(ISSUING_JURISDICTION, useSd) { issuingJurisdiction },
            claim(DOCUMENT_NUMBER, useSd) { UUID.randomUUID().toString() },
            claim(ADMINISTRATIVE_NUMBER, useSd) { UUID.randomUUID().toString() },
        )
    }


fun OidcUserInfoExtended.buildCompanyRegistrationClaims(useSd: Boolean) =
    with(CompanyRegistrationDataElements) {
        listOfNotNull(
            claim(COMPANY_NAME, useSd) { legalName },
            claim(COMPANY_TYPE, useSd) { "Einzelunternehmen" },
            claim(COMPANY_STATUS, useSd) { "economically active" },
            claim(COMPANY_ACTIVITY, useSd) {
                with(CompanyRegistrationDataElements.CompanyActivity) {
                    listOf(
                        claim(NACE_CODE, useSd) { "J62" },
                        //claim(ACTIVITY_DESCRIPTION, useSd){"7500"}
                    )
                }
            },
            claim(REGISTRATION_DATE, useSd) { LocalDate(2015, 6, 25) },
            //claim(COMPANY_END_DATE, useSd) { LocalDate(2025, Random.nextInt(1, 12), Random.nextInt(1, 28)) },
            claim(COMPANY_EUID, useSd) { "ATCHCUSP.69743824" },
            claim(VAT_NUMBER, useSd) { "ATU69743824" },
            claim(COMPANY_CONTACT_DATA, useSd) {
                with(CompanyRegistrationDataElements.ContactData) {
                    listOf(
                        claim(EMAIL, useSd) { "office@a-sit.at" },
                        claim(TELEPHONE, useSd) { "+43-555-${Random.nextInt(1, 9999)}" }
                    )
                }
            },
            claim(REGISTERED_ADDRESS, useSd) {
                with(CompanyRegistrationDataElements.Address) {
                    listOf(
                        claim(THOROUGHFARE, useSd) { "Seidlgasse" },
                        claim(LOCATOR_DESIGNATOR, useSd) { "22/9" },
                        claim(POST_CODE, useSd) { "1030" },
                        claim(POST_NAME, useSd) { "Wien" },
                        claim(ADMIN_UNIT_L_1, useSd) { "AT" },
                        claim(ADMIN_UNIT_L_2, useSd) { "Wien" }
                    )
                }
            },
        )
    }

fun OidcUserInfoExtended.buildCorClaims(iss: Instant, exp: Instant, useSd: Boolean) =
    with(CertificateOfResidenceDataElements) {
        val (postCode, city, state, street, locator) = addressOrRandom()
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
            claim(RESIDENCE_ADDRESS_THOROUGHFARE, useSd) { street },
            claim(RESIDENCE_ADDRESS_LOCATOR_DESIGNATOR, useSd) { locator },
            claim(RESIDENCE_ADDRESS_POST_CODE, useSd) { postCode },
            claim(RESIDENCE_ADDRESS_POST_NAME, useSd) { city },
            claim(RESIDENCE_ADDRESS_ADMIN_UNIT_L_1, useSd) { country },
            claim(RESIDENCE_ADDRESS_ADMIN_UNIT_L_2, useSd) { state },
            claim(RESIDENCE_ADDRESS_FULL_ADDRESS, useSd) { fullAddress },
            claim(GENDER, useSd) { gender },
            claim(BIRTH_PLACE, useSd) { randomAddress().city },
            claim(ARRIVAL_DATE, useSd) { arrivalDate },
            claim(NATIONALITY, useSd) { nationality },
            claim(ISSUANCE_DATE, useSd) { iss },
            claim(EXPIRY_DATE, useSd) { exp },
            claim(ISSUING_AUTHORITY, useSd) { issuingAuthority },
            claim(DOCUMENT_NUMBER, useSd) { UUID.randomUUID().toString() },
            claim(ADMINISTRATIVE_NUMBER, useSd) { UUID.randomUUID().toString() },
            claim(ISSUING_COUNTRY, useSd) { issuingCountry },
            claim(ISSUING_JURISDICTION, useSd) { issuingJurisdiction },
        )
    }


fun OidcUserInfoExtended.buildMdlClaims(useSd: Boolean) =
    with(MobileDrivingLicenceDataElements) {
        val (postCode, city, state, street, locator) = addressOrRandom()
        val country = userInfo.address?.country ?: fallbackAddressCountry
        val formatted = userInfo.address?.formatted ?: formatAddress(street, locator, postCode, city)
        listOfNotNull(
            claim(FAMILY_NAME, useSd) { userInfo.familyName },
            claim(GIVEN_NAME, useSd) { userInfo.givenName },
            claim(BIRTH_DATE, useSd) { dateOfBirth },
            claim(ISSUE_DATE, useSd) { issueDate() },
            claim(EXPIRY_DATE, useSd) { expiryDate() },
            claim(ISSUING_COUNTRY, useSd) { issuingCountry },
            claim(ISSUING_AUTHORITY, useSd) { issuingAuthority },
            claim(DOCUMENT_NUMBER, useSd) { UUID.randomUUID().toString() },
            claim(PORTRAIT, useSd) { portrait },
            claim(DRIVING_PRIVILEGES, useSd) { arrayOf(fakeDrivingPrivilege()) },
            claim(UN_DISTINGUISHING_SIGN, useSd) { unDistinguishingSign },
            claim(ADMINISTRATIVE_NUMBER, useSd) { UUID.randomUUID().toString() },
            claim(SEX, useSd) { sex },
            claim(HEIGHT, useSd) { Random.nextUInt(150u, 210u) },
            claim(WEIGHT, useSd) { Random.nextUInt(60u, 120u) },
            claim(EYE_COLOUR, useSd) { randomEyeColour() },
            claim(HAIR_COLOUR, useSd) { randomHairColour() },
            claim(BIRTH_PLACE, useSd) { randomAddress().city },
            claim(RESIDENT_ADDRESS, useSd) { formatted },
            claim(PORTRAIT_CAPTURE_DATE, useSd) { portraitCaptureDate },
            claim(AGE_IN_YEARS, useSd) { ageInYears },
            claim(AGE_BIRTH_YEAR, useSd) { dateOfBirth.year.toUInt() },
            claim(AGE_OVER_12, useSd) { ageOver12 },
            claim(AGE_OVER_14, useSd) { ageOver14 },
            claim(AGE_OVER_16, useSd) { ageOver16 },
            claim(AGE_OVER_18, useSd) { ageOver18 },
            claim(AGE_OVER_21, useSd) { ageOver21 },
            claim(ISSUING_JURISDICTION, useSd) { issuingJurisdiction },
            claim(NATIONALITY, useSd) { nationality },
            claim(RESIDENT_CITY, useSd) { city },
            claim(RESIDENT_STATE, useSd) { state },
            claim(RESIDENT_POSTAL_CODE, useSd) { postCode },
            claim(RESIDENT_COUNTRY, useSd) { country },
            claim(FAMILY_NAME_NATIONAL_CHARACTER, useSd) { userInfo.familyName },
            claim(GIVEN_NAME_NATIONAL_CHARACTER, useSd) { userInfo.givenName },
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

private fun claim(key: String, useSd: Boolean, value: () -> Any?): ClaimToBeIssued? =
    value()?.let { ClaimToBeIssued(key, it.encodeIfNeeded(), useSd) }

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
