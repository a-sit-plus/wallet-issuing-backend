package at.asitplus.wallet.backend.config

import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.wallet.companyregistration.CompanyRegistrationDataElements
import at.asitplus.wallet.companyregistration.CompanyRegistrationScheme
import at.asitplus.wallet.cor.CertificateOfResidenceDataElements
import at.asitplus.wallet.cor.CertificateOfResidenceScheme
import at.asitplus.wallet.cor.ResidenceAddress
import at.asitplus.wallet.eprescription.EPrescriptionDataElements
import at.asitplus.wallet.eprescription.EPrescriptionScheme
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.eupid.IsoIec5218Gender
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.lib.agent.ClaimToBeIssued
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.iso.IssuerSignedItem
import at.asitplus.wallet.mdl.DrivingPrivilege
import at.asitplus.wallet.mdl.IsoSexEnum
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
import at.asitplus.wallet.por.PowerOfRepresentationDataElements
import at.asitplus.wallet.por.PowerOfRepresentationScheme
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import kotlinx.serialization.encodeToString
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
        is EuPidScheme -> if (representation == CredentialRepresentation.SD_JWT)
            userInfo.buildEupidClaimsSdJwt(claims, iss, exp)
        else
            userInfo.buildEupidClaims(claims, iss, exp)

        is MobileDrivingLicenceScheme -> userInfo.buildMdlClaims(claims)
        is PowerOfRepresentationScheme -> userInfo.buildPorClaims(claims, iss, exp)
        is CertificateOfResidenceScheme -> userInfo.buildCorClaims(claims, iss, exp)
        is EPrescriptionScheme -> userInfo.buildEPrescriptionClaims(claims, loader)
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
        val claimsWithNewNames = listOfNotNull(
            claims.whenRequested(FAMILY_NAME) { userInfo.familyName },
            claims.whenRequested(GIVEN_NAME) { userInfo.givenName },
            claims.whenRequested(BIRTH_DATE) { dateOfBirth },
            claims.whenRequested(PREFIX_AGE_EQUAL_OR_OVER) {
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
            claims.whenRequested(AGE_EQUAL_OR_OVER_12) { ageOver12 },
            claims.whenRequested(AGE_EQUAL_OR_OVER_14) { ageOver14 },
            claims.whenRequested(AGE_EQUAL_OR_OVER_16) { ageOver16 },
            claims.whenRequested(AGE_EQUAL_OR_OVER_18) { ageOver18 },
            claims.whenRequested(AGE_EQUAL_OR_OVER_21) { ageOver21 },
            claims.whenRequested(AGE_IN_YEARS) { ageInYears },
            claims.whenRequested(AGE_BIRTH_YEAR) { dateOfBirth.year.toUInt() },
            claims.whenRequested(FAMILY_NAME_BIRTH) { userInfo.familyName },
            claims.whenRequested(GIVEN_NAME_BIRTH) { userInfo.givenName },
            claims.whenRequested(PREFIX_PLACE_OF_BIRTH) {
                with(EuPidScheme.SdJwtAttributes.PlaceOfBirth) {
                    listOf(
                        ClaimToBeIssued(COUNTRY, fallbackBirthCountry),
                        ClaimToBeIssued(REGION, ourBirthState),
                        ClaimToBeIssued(LOCALITY, ourBirthCity),
                    )
                }
            },
            claims.whenRequested(PLACE_OF_BIRTH_COUNTRY) { fallbackBirthCountry },
            claims.whenRequested(PLACE_OF_BIRTH_REGION) { ourBirthState },
            claims.whenRequested(PLACE_OF_BIRTH_LOCALITY) { ourBirthCity },
            claims.whenRequested(PREFIX_PLACE_OF_BIRTH) {
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
            claims.whenRequested(ADDRESS_FORMATTED) { formatted },
            claims.whenRequested(ADDRESS_COUNTRY) { country },
            claims.whenRequested(ADDRESS_REGION) { state },
            claims.whenRequested(ADDRESS_LOCALITY) { city },
            claims.whenRequested(ADDRESS_POSTAL_CODE) { postCode },
            claims.whenRequested(ADDRESS_STREET) { street },
            claims.whenRequested(ADDRESS_HOUSE_NUMBER) { locator.toString() },
            claims.whenRequested(GENDER) { genderText },
            claims.whenRequested(NATIONALITIES) { listOf(nationality) },
            claims.whenRequested(ISSUANCE_DATE) { iss },
            claims.whenRequested(EXPIRY_DATE) { exp },
            claims.whenRequested(ISSUING_AUTHORITY) { issuingAuthority },
            claims.whenRequested(DOCUMENT_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequested(ADMINISTRATIVE_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequested(ISSUING_COUNTRY) { issuingCountry },
            claims.whenRequested(ISSUING_JURISDICTION) { issuingJurisdiction },
        )
        claimsWithNewNames + buildEupidClaims(claims, iss, exp).filter {
            it.name !in claimsWithNewNames.map { it.name }
        }// for backwards compatibility with older wallets
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
            claims.whenRequested(AGE_OVER_12) { ageOver12 },
            claims.whenRequested(AGE_OVER_14) { ageOver14 },
            claims.whenRequested(AGE_OVER_16) { ageOver16 },
            claims.whenRequested(AGE_OVER_18) { ageOver18 },
            claims.whenRequested(AGE_OVER_21) { ageOver21 },
            claims.whenRequested(AGE_IN_YEARS) { ageInYears },
            claims.whenRequested(AGE_BIRTH_YEAR) { dateOfBirth.year.toUInt() },
            claims.whenRequested(FAMILY_NAME_BIRTH) { userInfo.familyName },
            claims.whenRequested(GIVEN_NAME_BIRTH) { userInfo.givenName },
            claims.whenRequested(BIRTH_PLACE) { ourBirthStreet },
            claims.whenRequested(BIRTH_COUNTRY) { fallbackBirthCountry },
            claims.whenRequested(BIRTH_STATE) { ourBirthState },
            claims.whenRequested(BIRTH_CITY) { ourBirthCity },
            claims.whenRequested(RESIDENT_ADDRESS) { formatted },
            claims.whenRequested(RESIDENT_COUNTRY) { country },
            claims.whenRequested(RESIDENT_STATE) { state },
            claims.whenRequested(RESIDENT_CITY) { city },
            claims.whenRequested(RESIDENT_POSTAL_CODE) { postCode },
            claims.whenRequested(RESIDENT_STREET) { street },
            claims.whenRequested(RESIDENT_HOUSE_NUMBER) { locator.toString() },
            claims.whenRequested(GENDER) { gender },
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
            claims.whenRequested(LEGAL_PERSON_IDENTIFIER) { legalPersonIdentifier },
            claims.whenRequested(LEGAL_NAME) { legalName },
            claims.whenRequested(FULL_POWERS) { true },
            claims.whenRequested(E_SERVICE) { eService },
            claims.whenRequested(EFFECTIVE_FROM_DATE) { iss },
            claims.whenRequested(EFFECTIVE_UNTIL_DATE) { exp },
            claims.whenRequested(ISSUANCE_DATE) { iss },
            claims.whenRequested(EXPIRY_DATE) { exp },
            claims.whenRequested(ISSUING_AUTHORITY) { issuingAuthority },
            claims.whenRequested(ISSUING_COUNTRY) { issuingCountry },
            claims.whenRequested(ISSUING_JURISDICTION) { issuingJurisdiction },
            claims.whenRequested(DOCUMENT_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequested(ADMINISTRATIVE_NUMBER) { UUID.randomUUID().toString() },
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
                        ClaimToBeIssued(NACE_CODE, "7500"),
                        ClaimToBeIssued(ACTIVITY_DESCRIPTION, "7500")
                    )
                }
            },
            claims.whenRequested(REGISTRATION_DATE) { LocalDate(2020, Random.nextInt(1, 12), Random.nextInt(1, 28)) },
            claims.whenRequested(COMPANY_END_DATE) { LocalDate(2025, Random.nextInt(1, 12), Random.nextInt(1, 28)) },
            claims.whenRequested(COMPANY_EUID) { "ATCHCUSP.90000${Random.nextInt(100, 999)}" },
            claims.whenRequested(VAT_NUMBER) { "9999${Random.nextInt(1000, 9999)}" },
            claims.whenRequested(COMPANY_CONTACT_DATA) {
                with(CompanyRegistrationDataElements.ContactData) {
                    listOf(
                        ClaimToBeIssued(EMAIL, "${userInfo.givenName}@example.com"),
                        ClaimToBeIssued(TELEPHONE, "+43-555-${Random.nextInt(1, 9999)}")
                    )
                }
            },
            claims.whenRequested(REGISTERED_ADDRESS) {
                with(CompanyRegistrationDataElements.Address) {
                    with(randomAddress()) {
                        listOf(
                            ClaimToBeIssued(THOROUGHFARE, street),
                            ClaimToBeIssued(LOCATOR_DESIGNATOR, locator.toString()),
                            ClaimToBeIssued(POST_CODE, postCode),
                            ClaimToBeIssued(POST_NAME, city),
                            ClaimToBeIssued(ADMIN_UNIT_L_1, "AT"),
                            ClaimToBeIssued(ADMIN_UNIT_L_2, state)
                        )
                    }
                }
            },
        )
    }

fun OidcUserInfoExtended.buildCorClaims(claims: Collection<String>?, iss: Instant, exp: Instant) =
    with(CertificateOfResidenceDataElements) {
        listOfNotNull(
            claims.whenRequested(FAMILY_NAME) { userInfo.familyName },
            claims.whenRequested(GIVEN_NAME) { userInfo.givenName },
            claims.whenRequested(BIRTH_DATE) { dateOfBirth },
            claims.whenRequested(RESIDENCE_ADDRESS) { residenceAddress },
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


fun OidcUserInfoExtended.buildEPrescriptionClaims(claims: Collection<String>?, loader: EPrescriptionLoader) =
    with(EPrescriptionDataElements) {
        val ottElement =
            loader.load(bpk, userInfo.givenName!!, userInfo.familyName!!, userInfo.birthDate!!).getOrNull()?.data
                ?: throw IllegalArgumentException("No data from EPrescriptionLoader")
        listOfNotNull(
            claims.whenRequested(OTT) { ottElement.oneTimeToken },
            claims.whenRequested(COUNTRY_CODE) { ottElement.countryCode },
            claims.whenRequested(VALID_UNTIL) { ottElement.ottValidUntil },
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
        ?: getClaims(
            "urn:pvpgvat:oidc.mandator_natural_person_given_name",
            "urn:pvpgvat:oidc.mandator_natural_person_family_name"
        )
        ?: userInfo.givenName?.let {
            (userInfo.givenName + " " + userInfo.familyName)
        } ?: userInfo.name
        ?: userInfo.subject


val OidcUserInfoExtended.legalPersonIdentifier: String
    get() = getClaimAsString("urn:pvpgvat:oidc.mandator_legal_person_source_pin")
        ?: getClaimAsString("urn:pvpgvat:oidc.mandator_natural_person_bpk")
        ?: userInfo.subject

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

val OidcUserInfoExtended.residenceAddress: String
    get() {
        val (postCode, city, state, street, locator) = addressOrRandom()
        val country = userInfo.address?.country ?: fallbackAddressCountry
        val fullAddress = formatAddress(street, locator, postCode, city)
        return Json.encodeToString(
            ResidenceAddress(
                thoroughfare = street,
                locatorDesignator = locator.toString(),
                postCode = postCode,
                postName = city,
                adminUnitLevel1 = country,
                adminUnitLevel2 = state,
                fullAddress = fullAddress,
            )
        )
    }

private fun formatAddress(street: String, locator: Int, postalCode: String, city: String) =
    "$street $locator, $postalCode $city"

private fun Collection<String>?.whenRequested(key: String, value: () -> Any?): ClaimToBeIssued? =
    if (isNullOrContains(key)) value()?.let { ClaimToBeIssued(key, it.encodeIfNeeded()) } else null

fun Collection<String>?.isNullOrContains(name: String) =
    this == null || contains(name)

@OptIn(ExperimentalEncodingApi::class)
fun Any.encodeIfNeeded() = if (this is ByteArray) kotlin.io.encoding.Base64.encode(this) else this

private fun expiryDate() = LocalDate.parse("2025-12-31")

private fun issueDate() = LocalDate.parse("2023-01-01")

private val eService = "Dummy Service"
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
