package at.asitplus.wallet.backend.config

import at.asitplus.crypto.datatypes.CryptoPublicKey
import at.asitplus.wallet.cor.CertificateOfResidenceDataElements
import at.asitplus.wallet.cor.CertificateOfResidenceScheme
import at.asitplus.wallet.cor.ResidenceAddress
import at.asitplus.wallet.eprescription.EPrescriptionDataElements
import at.asitplus.wallet.eprescription.EPrescriptionScheme
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.lib.agent.ClaimToBeIssued
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.iso.IssuerSignedItem
import at.asitplus.wallet.lib.oidvci.OidcUserInfoExtended
import at.asitplus.wallet.mdl.DrivingPrivilege
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
import at.asitplus.wallet.por.PowerOfRepresentationDataElements
import at.asitplus.wallet.por.PowerOfRepresentationScheme
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

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
            dateOfBirth = dateOfBirth ?: LocalDate.fromEpochDays(0),
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
            birthDate = dateOfBirth ?: LocalDate.fromEpochDays(0),
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
            claims.whenRequested(ISSUANCE_DATE) { iss },
            claims.whenRequested(EXPIRY_DATE) { exp },
            claims.whenRequested(ISSUING_AUTHORITY) { "Miniwahr" },
            claims.whenRequested(ISSUING_COUNTRY) { "AT" },
        )
    }

fun OidcUserInfoExtended.buildPorClaims(claims: Collection<String>?, iss: Instant, exp: Instant) =
    with(PowerOfRepresentationDataElements) {
        listOfNotNull(
            claims.whenRequested(LEGAL_PERSON_IDENTIFIER) { legalPersonIdentifier },
            claims.whenRequested(LEGAL_NAME) { legalName },
            claims.whenRequested(FULL_POWERS) { true },
            claims.whenRequested(EFFECTIVE_FROM_DATE) { iss },
            claims.whenRequested(ISSUANCE_DATE) { iss },
            claims.whenRequested(EXPIRY_DATE) { exp },
            claims.whenRequested(ISSUING_AUTHORITY) { "Miniwahr" },
            claims.whenRequested(ISSUING_COUNTRY) { "AT" },
        )
    }

fun OidcUserInfoExtended.buildCorClaims(claims: Collection<String>?, iss: Instant, exp: Instant) =
    with(CertificateOfResidenceDataElements) {
        listOfNotNull(
            claims.whenRequested(FAMILY_NAME) { userInfo.familyName },
            claims.whenRequested(GIVEN_NAME) { userInfo.givenName },
            claims.whenRequested(BIRTH_DATE) { dateOfBirth },
            claims.whenRequested(RESIDENCE_ADDRESS) { residenceAddress },
            claims.whenRequested(ISSUANCE_DATE) { iss },
            claims.whenRequested(EXPIRY_DATE) { exp },
            claims.whenRequested(ISSUING_AUTHORITY) { "Miniwahr" },
            claims.whenRequested(ISSUING_COUNTRY) { "AT" },
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
            claims.whenRequested(ISSUING_AUTHORITY) { "Miniwahr" },
            claims.whenRequested(ISSUING_COUNTRY) { "AT" },
            claims.whenRequested(UN_DISTINGUISHING_SIGN) { "A" },
            claims.whenRequested(DRIVING_PRIVILEGES) { arrayOf(fakeDrivingPrivilege()) },
            claims.whenRequested(EXPIRY_DATE) { LocalDate.parse("2025-12-31") },
            claims.whenRequested(DOCUMENT_NUMBER) { "123456" + Random.nextLong(1000, 9999) },
            claims.whenRequested(PORTRAIT) { portrait },
            claims.whenRequested(AGE_OVER_18) { ageOver18 },
        )
    }

fun fakeDrivingPrivilege() =
    DrivingPrivilege("B", LocalDate.parse("2023-01-01"), LocalDate.parse("2025-12-31"))

val OidcUserInfoExtended.bpk: String
    get() = getClaimAsString("urn:pvpgvat:oidc.bpk")
        ?: userInfo.subject

val OidcUserInfoExtended.dateOfBirth
    get() = userInfo.birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

val OidcUserInfoExtended.ageOver14
    get() = getClaimAsString("org.iso.18013.5.1:age_over_14")?.let { it.toBoolean() }
        ?: ageOver16

val OidcUserInfoExtended.ageOver16
    get() = getClaimAsString("org.iso.18013.5.1:age_over_16")?.let { it.toBoolean() }
        ?: ageOver18

val OidcUserInfoExtended.ageOver18: Boolean?
    get() = userInfo.ageOver18
        ?: getClaimAsString("org.iso.18013.5.1:age_over_18")?.let { it.toBoolean() }

fun Instant.toLocalDate() = toLocalDateTime(TimeZone.currentSystemDefault()).date

val OidcUserInfoExtended.ageOver21: Boolean?
    get() = getClaimAsString("org.iso.18013.5.1:age_over_21")?.let { it.toBoolean() }
        ?: dateOfBirth?.let { it < Clock.System.now().toLocalDate().minus(DatePeriod(21)) }

val OidcUserInfoExtended.portrait: ByteArray?
    get() = userInfo.picture?.decodeToByteArray(Base64())
        ?: getClaimAsString("org.iso.18013.5.1:portrait")?.decodeToByteArray(Base64())

val OidcUserInfoExtended.mainAddress: String?
    get() = userInfo.address?.formatted
        ?: getClaimAsString("urn:eidgvat:attributes.mainAddress")

val OidcUserInfoExtended.legalName: String
    get() = getClaimAsString("urn:oid:1.2.40.0.10.2.1.1.261.84")
        ?: userInfo.name ?: userInfo.familyName ?: userInfo.subject

val OidcUserInfoExtended.legalPersonIdentifier: String
    get() = getClaimAsString("urn:oid:1.2.40.0.10.2.1.1.261.100")
        ?: userInfo.name ?: userInfo.familyName ?: userInfo.subject

fun OidcUserInfoExtended.getClaimAsString(key: String): String? {
    val element = jsonObject[key]
    if (element is JsonPrimitive) {
        return element.content
    }
    return element?.toString()
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
            fullAddress = mainAddress,
        )
    )

private fun Collection<String>?.whenRequested(key: String, value: () -> Any?): ClaimToBeIssued? =
    if (isNullOrContains(key)) value()?.let { ClaimToBeIssued(key, it.encodeIfNeeded()) } else null

fun Collection<String>?.isNullOrContains(name: String) =
    this == null || contains(name)

@OptIn(ExperimentalEncodingApi::class)
fun Any.encodeIfNeeded() = if (this is ByteArray) kotlin.io.encoding.Base64.encode(this) else this
