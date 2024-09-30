package at.asitplus.wallet.backend.config

import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.CryptoPublicKey
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
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
    claims: Collection<String>?,
    userInfo: OidcUserInfoExtended,
    iss: Instant,
    exp: Instant,
    loader: EPrescriptionLoader
): List<ClaimToBeIssued> =
    when (this) {
        is IdAustriaScheme -> userInfo.buildIdaClaims(claims)
        is EuPidScheme -> userInfo.buildEupidClaims(claims, iss, exp)
        is MobileDrivingLicenceScheme -> userInfo.buildMdlClaims(claims)
        is PowerOfRepresentationScheme -> userInfo.buildPorClaims(claims, iss, exp)
        is CertificateOfResidenceScheme -> userInfo.buildCorClaims(claims, iss, exp)
        is EPrescriptionScheme -> userInfo.buildEPrescriptionClaims(claims, loader)
        else -> TODO("$this is not implemented in buildClaims()")
    }.also { Napier.v("${this}.buildClaims returns $it") }

fun List<ClaimToBeIssued>.toIsoClaims(
    exp: Instant,
) = CredentialToBeIssued.Iso(
    issuerSignedItems = this
        .mapIndexed { idx, it -> it.buildIssuerSignedItem(idx) },
    expiration = exp
)

fun List<ClaimToBeIssued>.toSdJwtClaims(
    exp: Instant,
) = CredentialToBeIssued.VcSd(
    claims = this,
    expiration = exp
)

fun OidcUserInfoExtended.toIdaCredential(pubKey: CryptoPublicKey, exp: Instant) =
    CredentialToBeIssued.VcJwt(
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
    )

fun OidcUserInfoExtended.toEuPidCredential(pubKey: CryptoPublicKey, iss: Instant, exp: Instant) =
    CredentialToBeIssued.VcJwt(
        subject = EuPidCredential(
            id = pubKey.didEncoded,
            familyName = userInfo.familyName ?: "N/A",
            givenName = userInfo.givenName ?: "N/A",
            birthDate = dateOfBirth,
            ageOver18 = ageOver18,
            issuanceDate = iss,
            expiryDate = exp,
            issuingAuthority = "Miniwahr",
            issuingCountry = "AT",
        ).also { Napier.v("eupidVcJwt returns $it") },
        expiration = exp,
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
            claims.whenRequested(AGE_OVER_18) { ageOver18 },
        )
    }

fun OidcUserInfoExtended.buildEupidClaims(claims: Collection<String>?, iss: Instant, exp: Instant) =
    with(EuPidScheme.Attributes) {
        listOfNotNull(
            claims.whenRequested(FAMILY_NAME) { userInfo.familyName },
            claims.whenRequested(GIVEN_NAME) { userInfo.givenName },
            claims.whenRequested(BIRTH_DATE) { dateOfBirth },
            claims.whenRequested(AGE_OVER_18) { ageOver18 },
            claims.whenRequested(AGE_IN_YEARS) { ageInYears },
            claims.whenRequested(AGE_BIRTH_YEAR) { dateOfBirth.year.toUInt() },
            claims.whenRequested(FAMILY_NAME_BIRTH) { userInfo.familyName },
            claims.whenRequested(GIVEN_NAME_BIRTH) { userInfo.givenName },
            claims.whenRequested(BIRTH_PLACE) { birthPlace },
            claims.whenRequested(BIRTH_COUNTRY) { birthCountry },
            claims.whenRequested(BIRTH_STATE) { birthState },
            claims.whenRequested(BIRTH_CITY) { birthCity },
            claims.whenRequested(RESIDENT_ADDRESS) { userInfo.address?.formatted ?: fullAddress },
            claims.whenRequested(RESIDENT_COUNTRY) { userInfo.address?.country ?: "US" },
            claims.whenRequested(RESIDENT_STATE) { userInfo.address?.region ?: "CA" },
            claims.whenRequested(RESIDENT_CITY) { userInfo.address?.locality ?: "Hill Valley" },
            claims.whenRequested(RESIDENT_POSTAL_CODE) { userInfo.address?.postalCode ?: "90210" },
            claims.whenRequested(RESIDENT_STREET) { userInfo.address?.street ?: "Riverside Drive" },
            claims.whenRequested(RESIDENT_HOUSE_NUMBER) { "1640" },
            claims.whenRequested(GENDER) { gender },
            claims.whenRequested(NATIONALITY) { nationality },
            claims.whenRequested(ISSUANCE_DATE) { iss },
            claims.whenRequested(EXPIRY_DATE) { exp },
            claims.whenRequested(ISSUING_AUTHORITY) { "Miniwahr" },
            claims.whenRequested(DOCUMENT_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequested(ADMINISTRATIVE_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequested(ISSUING_COUNTRY) { "AT" },
            claims.whenRequested(ISSUING_JURISDICTION) { "AT-0" },
        )
    }

fun OidcUserInfoExtended.buildPorClaims(claims: Collection<String>?, iss: Instant, exp: Instant) =
    with(PowerOfRepresentationDataElements) {
        listOfNotNull(
            claims.whenRequested(LEGAL_PERSON_IDENTIFIER) { legalPersonIdentifier },
            claims.whenRequested(LEGAL_NAME) { legalName },
            claims.whenRequested(FULL_POWERS) { true },
            claims.whenRequested(E_SERVICE) { "Dummy Service" },
            claims.whenRequested(EFFECTIVE_FROM_DATE) { iss },
            claims.whenRequested(EFFECTIVE_UNTIL_DATE) { exp },
            claims.whenRequested(ISSUANCE_DATE) { iss },
            claims.whenRequested(EXPIRY_DATE) { exp },
            claims.whenRequested(ISSUING_AUTHORITY) { "Miniwahr" },
            claims.whenRequested(ISSUING_COUNTRY) { "AT" },
            claims.whenRequested(ISSUING_JURISDICTION) { "AT-0" },
            claims.whenRequested(DOCUMENT_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequested(ADMINISTRATIVE_NUMBER) { UUID.randomUUID().toString() },
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
            claims.whenRequested(BIRTH_PLACE) { birthPlace },
            claims.whenRequested(ARRIVAL_DATE) { arrivalDate },
            claims.whenRequested(NATIONALITY) { nationality },
            claims.whenRequested(ISSUANCE_DATE) { iss },
            claims.whenRequested(EXPIRY_DATE) { exp },
            claims.whenRequested(ISSUING_AUTHORITY) { "Miniwahr" },
            claims.whenRequested(DOCUMENT_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequested(ADMINISTRATIVE_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequested(ISSUING_COUNTRY) { "AT" },
            claims.whenRequested(ISSUING_JURISDICTION) { "AT-0" },
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
        listOfNotNull(
            claims.whenRequested(FAMILY_NAME) { userInfo.familyName },
            claims.whenRequested(GIVEN_NAME) { userInfo.givenName },
            claims.whenRequested(BIRTH_DATE) { dateOfBirth },
            claims.whenRequested(ISSUE_DATE) { LocalDate.parse("2023-01-01") },
            claims.whenRequested(EXPIRY_DATE) { LocalDate.parse("2025-12-31") },
            claims.whenRequested(ISSUING_COUNTRY) { "AT" },
            claims.whenRequested(ISSUING_AUTHORITY) { "Miniwahr" },
            claims.whenRequested(DOCUMENT_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequested(PORTRAIT) { portrait },
            claims.whenRequested(DRIVING_PRIVILEGES) { arrayOf(fakeDrivingPrivilege()) },
            claims.whenRequested(UN_DISTINGUISHING_SIGN) { "A" },
            claims.whenRequested(ADMINISTRATIVE_NUMBER) { UUID.randomUUID().toString() },
            claims.whenRequested(SEX) { sex },
            claims.whenRequested(HEIGHT) { Random.nextUInt(150u, 210u) },
            claims.whenRequested(WEIGHT) { Random.nextUInt(60u, 120u) },
            claims.whenRequested(EYE_COLOUR) { randomEyeColour() },
            claims.whenRequested(HAIR_COLOUR) { randomHairColour() },
            claims.whenRequested(BIRTH_PLACE) { birthPlace },
            claims.whenRequested(RESIDENT_ADDRESS) { userInfo.address?.locality ?: "Hill Valley" },
            claims.whenRequested(PORTRAIT_CAPTURE_DATE) { portraitCaptureDate },
            claims.whenRequested(AGE_IN_YEARS) { ageInYears },
            claims.whenRequested(AGE_BIRTH_YEAR) { dateOfBirth.year.toUInt() },
            claims.whenRequested(AGE_OVER_18) { ageOver18 },
            claims.whenRequested(ISSUING_JURISDICTION) { "AT-0 " },
            claims.whenRequested(NATIONALITY) { nationality },
            claims.whenRequested(RESIDENT_CITY) { userInfo.address?.locality ?: "Hill Valley" },
            claims.whenRequested(RESIDENT_STATE) { "CA" },
            claims.whenRequested(RESIDENT_POSTAL_CODE) { userInfo.address?.postalCode ?: "90210" },
            claims.whenRequested(RESIDENT_COUNTRY) { "US" },
            claims.whenRequested(FAMILY_NAME_NATIONAL_CHARACTER) { userInfo.familyName },
            claims.whenRequested(GIVEN_NAME_NATIONAL_CHARACTER) { userInfo.givenName },
        )
    }

private fun randomEyeColour() =
    listOf("black", "blue", "brown", "dichromatic", "grey", "green", "hazel", "maroon", "pink", "unknown").random()

private fun randomHairColour() =
    listOf("bald", "black", "blond", "brown", "grey", "red", "auburn", "sandy", "white", "unknown").random()

fun fakeDrivingPrivilege() = DrivingPrivilege(
    vehicleCategoryCode = "B",
    issueDate = LocalDate.parse("2023-01-01"),
    expiryDate = LocalDate.parse("2025-12-31")
)

val OidcUserInfoExtended.bpk: String
    get() = getClaimAsString("urn:pvpgvat:oidc.bpk")
        ?: userInfo.subject

val OidcUserInfoExtended.dateOfBirth
    get() = userInfo.birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate(1970, 1, 1)

val OidcUserInfoExtended.sex
    get() = getClaimAsString("urn:eidgvat:attributes.gender")?.toIsoSexEnum()
        ?: IsoSexEnum.NOT_KNOWN

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

val OidcUserInfoExtended.birthPlace: String
    get() = "$birthCity, $birthState, $birthCountry"

val OidcUserInfoExtended.birthCountry: String
    get() = userInfo.address?.country
        ?: "US"

val OidcUserInfoExtended.birthState: String
    get() = userInfo.address?.region
        ?: "CA"

val OidcUserInfoExtended.birthCity: String
    get() = userInfo.address?.locality
        ?: "Unterleuten"

val OidcUserInfoExtended.arrivalDate: LocalDate
    get() = getClaimAsString("urn:eidgvat:attributes.mainAddressRegistrationDate")
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate(2000, 1, 1)

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
        ?: userInfo.name ?: userInfo.familyName ?: userInfo.subject

val OidcUserInfoExtended.legalPersonIdentifier: String
    get() = getClaimAsString("urn:pvpgvat:oidc.mandator_legal_person_source_pin")
        ?: getClaimAsString("urn:pvpgvat:oidc.mandator_natural_person_bpk")
        ?: userInfo.name ?: userInfo.familyName ?: userInfo.subject

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
    get() = Json.encodeToString(
        ResidenceAddress(
            thoroughfare = userInfo.address?.street ?: "Riverside Drive",
            locatorDesignator = "1640",
            postCode = userInfo.address?.postalCode ?: "90210",
            postName = userInfo.address?.locality ?: "Hill Valley",
            adminUnitLevel1 = "US",
            adminUnitLevel2 = "CA",
            fullAddress = userInfo.address?.formatted ?: fullAddress,
        )
    )

private val OidcUserInfoExtended.fullAddress
    get() = "${userInfo.address?.street ?: "Riverside Drive"} 1640," +
            " ${userInfo.address?.postalCode ?: "90210"} ${userInfo.address?.locality ?: "Hill Valley"}"

private fun Collection<String>?.whenRequested(key: String, value: () -> Any?): ClaimToBeIssued? =
    if (isNullOrContains(key)) value()?.let { ClaimToBeIssued(key, it.encodeIfNeeded()) } else null

fun Collection<String>?.isNullOrContains(name: String) =
    this == null || contains(name)

@OptIn(ExperimentalEncodingApi::class)
fun Any.encodeIfNeeded() = if (this is ByteArray) kotlin.io.encoding.Base64.encode(this) else this
