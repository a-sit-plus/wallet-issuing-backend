package at.asitplus.wallet.backend.config

import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.wallet.companyregistration.CompanyRegistrationDataElements
import at.asitplus.wallet.companyregistration.CompanyRegistrationScheme
import at.asitplus.wallet.cor.CertificateOfResidenceDataElements
import at.asitplus.wallet.cor.CertificateOfResidenceScheme
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.eupid.IsoIec5218Gender
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtScheme
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
import at.asitplus.wallet.taxid.TaxId2025Scheme
import at.asitplus.wallet.taxid.TaxIdScheme
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.*
import java.nio.charset.Charset
import java.util.*
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
        is EuPidScheme -> userInfo.buildEupidClaims(iss, exp, this.useSd())
        is EuPidSdJwtScheme -> userInfo.buildEupidClaimsSdJwt(iss, exp, this.useSd())
        is HealthIdScheme -> userInfo.buildHealthIdClaims(iss, loader, this.useSd())
        is TaxIdScheme -> userInfo.buildTaxIdClaims(iss, exp, this.useSd())
        is TaxId2025Scheme -> userInfo.buildTaxIdClaims(iss, exp, this.useSd())
        is MobileDrivingLicenceScheme -> userInfo.buildMdlClaims(this.useSd())
        is PowerOfRepresentationScheme -> userInfo.buildPorClaims(iss, exp, this.useSd())
        is CertificateOfResidenceScheme -> userInfo.buildCorClaims(iss, exp, this.useSd())
        is CompanyRegistrationScheme -> userInfo.buildCompanyRegistrationClaims(this.useSd())
        else -> TODO("$this is not implemented in buildClaims()")
    }.also { Napier.v("${this}.buildClaims returns $it") }

fun ConstantIndex.CredentialScheme.useSd() = when (this) {
    is HealthIdScheme -> false
    is TaxIdScheme -> false
    is TaxId2025Scheme -> false
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
    with(EuPidSdJwtScheme.SdJwtAttributes) {
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
                with(EuPidSdJwtScheme.SdJwtAttributes.AgeEqualOrOver) {
                    listOf(
                        claim(EQUAL_OR_OVER_12, useSd) { ageOver12 },
                        claim(EQUAL_OR_OVER_13, useSd) { ageOver13 },
                        claim(EQUAL_OR_OVER_14, useSd) { ageOver14 },
                        claim(EQUAL_OR_OVER_16, useSd) { ageOver16 },
                        claim(EQUAL_OR_OVER_18, useSd) { ageOver18 },
                        claim(EQUAL_OR_OVER_21, useSd) { ageOver21 },
                        claim(EQUAL_OR_OVER_25, useSd) { ageOver25 },
                        claim(EQUAL_OR_OVER_60, useSd) { ageOver60 },
                        claim(EQUAL_OR_OVER_62, useSd) { ageOver62 },
                        claim(EQUAL_OR_OVER_65, useSd) { ageOver65 },
                        claim(EQUAL_OR_OVER_68, useSd) { ageOver68 },
                    )
                }
            },
            claim(AGE_EQUAL_OR_OVER_12, useSd) { ageOver12 },
            claim(AGE_EQUAL_OR_OVER_13, useSd) { ageOver13 },
            claim(AGE_EQUAL_OR_OVER_14, useSd) { ageOver14 },
            claim(AGE_EQUAL_OR_OVER_16, useSd) { ageOver16 },
            claim(AGE_EQUAL_OR_OVER_18, useSd) { ageOver18 },
            claim(AGE_EQUAL_OR_OVER_21, useSd) { ageOver21 },
            claim(AGE_EQUAL_OR_OVER_25, useSd) { ageOver25 },
            claim(AGE_EQUAL_OR_OVER_60, useSd) { ageOver60 },
            claim(AGE_EQUAL_OR_OVER_62, useSd) { ageOver62 },
            claim(AGE_EQUAL_OR_OVER_65, useSd) { ageOver65 },
            claim(AGE_EQUAL_OR_OVER_68, useSd) { ageOver68 },
            claim(AGE_IN_YEARS, useSd) { ageInYears },
            claim(AGE_BIRTH_YEAR, useSd) { dateOfBirth.year.toUInt() },
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
            claim(PLACE_OF_BIRTH_LOCALITY, useSd) { ourBirthCity },
            claim(PREFIX_PLACE_OF_BIRTH, useSd) {
                with(EuPidSdJwtScheme.SdJwtAttributes.Address) {
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
            claim(SEX, useSd) { gender },
            claim(NATIONALITIES, useSd) { setOf(nationality) },
            claim(ISSUANCE_DATE, useSd) { iss },
            claim(EXPIRY_DATE, useSd) { exp },
            claim(ISSUING_AUTHORITY, useSd) { issuingAuthority },
            claim(DOCUMENT_NUMBER, useSd) { UUID.randomUUID().toString() },
            claim(ISSUING_COUNTRY, useSd) { issuingCountry },
            claim(ISSUING_JURISDICTION, useSd) { issuingJurisdiction },
            claim(PERSONAL_ADMINISTRATIVE_NUMBER, useSd) { UUID.randomUUID().toString() },
            claim(EMAIL, useSd) { email },
            claim(PHONE_NUMBER, useSd) { phoneNumber },
            claim(TRUST_ANCHOR, useSd) { "https://wallet.a-sit.at/" },
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
            claim(PORTRAIT_CAPTURE_DATE, useSd) { portraitCaptureDate },
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
            claim(AGE_IN_YEARS, useSd) { ageInYears },
            claim(AGE_BIRTH_YEAR, useSd) { dateOfBirth.year.toUInt() },
            claim(TRUST_ANCHOR, useSd) { "https://wallet.a-sit.at/" },
            claim(LOCATION_STATUS, useSd) { "https://wallet.a-sit.at/" },
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
    with(TaxId2025Scheme.Attributes) {
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

fun OidcUserInfoExtended.buildHealthIdClaims(iss: Instant, loader: EPrescriptionLoader, useSd: Boolean) =
    with(HealthIdScheme.Attributes) {
        val ottElement =
            loader.load(bpk, userInfo.givenName!!, userInfo.familyName!!, userInfo.birthDate!!).getOrNull()?.data
                ?: throw IllegalArgumentException("No data from EPrescriptionLoader")
        listOfNotNull(
            claim(ONE_TIME_TOKEN, useSd) { ottElement.oneTimeToken },
            claim(AFFILIATION_COUNTRY, useSd) { ottElement.countryCode },
            claim(ISSUE_DATE, useSd) { iss },
            claim(EXPIRY_DATE, useSd) { ottElement.ottValidUntil },
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
            claim(ISSUING_JURISDICTION, useSd) { issuingJurisdiction },
            claim(NATIONALITY, useSd) { nationality },
            claim(RESIDENT_CITY, useSd) { city },
            claim(RESIDENT_STATE, useSd) { state },
            claim(RESIDENT_POSTAL_CODE, useSd) { postCode },
            claim(RESIDENT_COUNTRY, useSd) { country },
            claim(FAMILY_NAME_NATIONAL_CHARACTER, useSd) { userInfo.familyName },
            claim(GIVEN_NAME_NATIONAL_CHARACTER, useSd) { userInfo.givenName },
            claim(SIGNATURE_USUAL_MARK, useSd) { signature() },
            claim(BIOMETRIC_TEMPLATE_FACE, useSd) { signature() },
            claim(BIOMETRIC_TEMPLATE_FINGER, useSd) { signature() },
            claim(BIOMETRIC_TEMPLATE_SIGNATURE_SIGN, useSd) { signature() },
            claim(BIOMETRIC_TEMPLATE_IRIS, useSd) { signature() },
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
        ?: (dateOfBirth < Clock.System.now().toLocalDate().minus(DatePeriod(12)))

val OidcUserInfoExtended.ageOver13
    get() = getClaimAsString("org.iso.18013.5.1:age_over_13")?.toBoolean()
        ?: (dateOfBirth < Clock.System.now().toLocalDate().minus(DatePeriod(13)))

val OidcUserInfoExtended.ageOver14
    get() = getClaimAsString("org.iso.18013.5.1:age_over_14")?.toBoolean()
        ?: (dateOfBirth < Clock.System.now().toLocalDate().minus(DatePeriod(14)))

val OidcUserInfoExtended.ageOver16
    get() = getClaimAsString("org.iso.18013.5.1:age_over_16")?.toBoolean()
        ?: (dateOfBirth < Clock.System.now().toLocalDate().minus(DatePeriod(16)))

val OidcUserInfoExtended.ageOver18: Boolean
    get() = userInfo.ageOver18
        ?: getClaimAsString("org.iso.18013.5.1:age_over_18")?.toBoolean()
        ?: (dateOfBirth < Clock.System.now().toLocalDate().minus(DatePeriod(18)))

fun Instant.toLocalDate() = toLocalDateTime(TimeZone.currentSystemDefault()).date

val OidcUserInfoExtended.ageOver21: Boolean
    get() = getClaimAsString("org.iso.18013.5.1:age_over_21")?.toBoolean()
        ?: (dateOfBirth < Clock.System.now().toLocalDate().minus(DatePeriod(21)))

val OidcUserInfoExtended.ageOver25: Boolean
    get() = getClaimAsString("org.iso.18013.5.1:age_over_25")?.toBoolean()
        ?: (dateOfBirth < Clock.System.now().toLocalDate().minus(DatePeriod(25)))

val OidcUserInfoExtended.ageOver60: Boolean
    get() = getClaimAsString("org.iso.18013.5.1:age_over_60")?.toBoolean()
        ?: (dateOfBirth < Clock.System.now().toLocalDate().minus(DatePeriod(60)))

val OidcUserInfoExtended.ageOver62: Boolean
    get() = getClaimAsString("org.iso.18013.5.1:age_over_62")?.toBoolean()
        ?: (dateOfBirth < Clock.System.now().toLocalDate().minus(DatePeriod(62)))

val OidcUserInfoExtended.ageOver65: Boolean
    get() = getClaimAsString("org.iso.18013.5.1:age_over_65")?.toBoolean()
        ?: (dateOfBirth < Clock.System.now().toLocalDate().minus(DatePeriod(65)))

val OidcUserInfoExtended.ageOver68: Boolean
    get() = getClaimAsString("org.iso.18013.5.1:age_over_68")?.toBoolean()
        ?: (dateOfBirth < Clock.System.now().toLocalDate().minus(DatePeriod(68)))

val OidcUserInfoExtended.ageInYears: UInt
    get() = (Clock.System.now().toLocalDate().minus(dateOfBirth)).years.toUInt()

val OidcUserInfoExtended.portrait: ByteArray?
    get() = userInfo.picture?.decodeToByteArray(Base64())
        ?: getClaimAsString("org.iso.18013.5.1:portrait")?.decodeToByteArray(Base64())

val OidcUserInfoExtended.portraitCaptureDate: LocalDate?
    get() = getClaimAsString("org.iso.18013.5.1:portrait_capture_date")
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate(2020, Random.nextInt(1, 12), Random.nextInt(1, 28))

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
    value()?.let { ClaimToBeIssued(key, it, useSd) }

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

private fun signature() = """
    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
    vx2fPC3+/wCv9sLW1/B9vH//2Q==
""".trimIndent().decodeToByteArray(Base64())