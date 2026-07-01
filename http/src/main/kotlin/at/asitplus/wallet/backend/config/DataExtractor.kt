package at.asitplus.wallet.backend.config

import at.asitplus.iso.IssuerSignedItem
import at.asitplus.openid.OidcAddressClaim
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.io.Base64Strict
import at.asitplus.wallet.lib.agent.ClaimToBeIssued
import at.asitplus.wallet.lib.agent.ClaimToBeIssuedArrayElement
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.eupid.PlaceOfBirth
import at.asitplus.wallet.lib.data.IsoMdocCredentialScheme
import at.asitplus.wallet.lib.data.LocalDateOrInstant
import at.asitplus.wallet.lib.data.SdJwtCredentialScheme
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlin.random.Random
import kotlin.time.Instant

/**
 * Builds the (fabricated) claims for each credential, dispatched by the credential's vct / ISO docType. Claim names
 * are plain strings matching the remote type metadata documents; complex values are encoded by the serializers
 * registered in [registerCredentialSerializers].
 */
fun SdJwtCredentialScheme.buildSdJwtClaims(
    userInfo: OidcUserInfoExtended,
    iss: Instant,
    exp: Instant,
    subjectPublicKey: CryptoPublicKey,
) = CredentialToBeIssued.VcSd(
    claims = when (sdJwtType) {
        "urn:eudi:pid:1" -> userInfo.buildEupidClaimsSdJwt(true)
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
).also { Napier.v("${this}.buildSdJwtClaims returns $it") }

fun IsoMdocCredentialScheme.buildIsoClaims(
    userInfo: OidcUserInfoExtended,
    exp: Instant,
    subjectPublicKey: CryptoPublicKey,
) = CredentialToBeIssued.Iso(
    issuerSignedItems = when (this.isoDocType) {
        "eu.europa.ec.eudi.pid.1" -> userInfo.buildEupidClaims(true)
        "org.iso.18013.5.1.mDL" -> userInfo.buildMdlClaims(true)
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

fun OidcUserInfoExtended.buildEupidClaimsSdJwt(useSd: Boolean): List<ClaimToBeIssued> {
    val address = addressOrRandom
    val birthAddress = randomAddress
    return listOfNotNull(
        claim("family_name", useSd) { userInfo.familyName },
        claim("given_name", useSd) { userInfo.givenName },
        claim("birthdate", useSd) { dateOfBirth },
        claim("picture", useSd) { portrait?.let { "data:image/jpeg;base64,${it.encodeToString(Base64Strict)}" } },
        claim("birth_family_name", useSd) { userInfo.familyName },
        claim("birth_given_name", useSd) { userInfo.givenName },
        claim("place_of_birth", useSd) {
            listOf(
                claim("locality", useSd) { birthAddress.city },
                claim("country", useSd) { birthAddress.country },
                claim("region", useSd) { birthAddress.state },
            )
        },
        claim("address", useSd) {
            listOf(
                claim("formatted", useSd) { address.formatted },
                claim("country", useSd) { address.country },
                claim("region", useSd) { address.state },
                claim("locality", useSd) { address.city },
                claim("postal_code", useSd) { address.postCode },
                claim("street_address", useSd) { address.street },
                claim("house_number", useSd) { address.locator },
            )
        },
        claim("sex", useSd) { gender },
        claim("nationalities", useSd) { setOf(ClaimToBeIssuedArrayElement(nationality)) },
        claim("date_of_issuance", useSd) { LocalDateOrInstant.LocalDate(issueDate) },
        claim("date_of_expiry", useSd) { LocalDateOrInstant.LocalDate(expiryDate) },
        claim("issuing_authority", useSd) { issuingAuthority },
        claim("document_number", useSd) { randomIdentifier },
        claim("issuing_country", useSd) { issuingCountry },
        claim("issuing_jurisdiction", useSd) { issuingJurisdiction },
        claim("personal_administrative_number", useSd) { randomIdentifier },
        claim("email", useSd) { email },
        claim("phone_number", useSd) { phoneNumber },
        claim("trust_anchor", useSd) { trustAnchor },
    )
}

fun OidcUserInfoExtended.buildEupidClaims(useSd: Boolean): List<ClaimToBeIssued> {
    val address = addressOrRandom
    val birthAddress = randomAddress
    return listOfNotNull(
        claim("family_name", useSd) { userInfo.familyName },
        claim("given_name", useSd) { userInfo.givenName },
        claim("birth_date", useSd) { dateOfBirth },
        claim("place_of_birth", useSd) {
            PlaceOfBirth(country = birthAddress.country, region = birthAddress.state, locality = birthAddress.city)
        },
        claim("nationality", useSd) { setOf(nationality) },
        claim("resident_address", useSd) { address.formatted },
        claim("resident_country", useSd) { address.country },
        claim("resident_state", useSd) { address.state },
        claim("resident_city", useSd) { address.city },
        claim("resident_postal_code", useSd) { address.postCode },
        claim("resident_street", useSd) { address.street },
        claim("resident_house_number", useSd) { address.locator.toString() },
        claim("personal_administrative_number", useSd) { randomIdentifier },
        claim("portrait", useSd) { portrait },
        claim("family_name_birth", useSd) { userInfo.familyName },
        claim("given_name_birth", useSd) { userInfo.givenName },
        claim("sex", useSd) { gender.code },
        claim("email_address", useSd) { email },
        claim("mobile_phone_number", useSd) { phoneNumber },
        claim("expiry_date", useSd) { LocalDateOrInstant.LocalDate(expiryDate) },
        claim("issuing_authority", useSd) { issuingAuthority },
        claim("issuing_country", useSd) { issuingCountry },
        claim("document_number", useSd) { randomIdentifier },
        claim("issuing_jurisdiction", useSd) { issuingJurisdiction },
        claim("issuance_date", useSd) { LocalDateOrInstant.LocalDate(issueDate) },
        claim("trust_anchor", useSd) { trustAnchor },
        claim("location_status", useSd) { trustAnchor },
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
    listOfNotNull(
        claim("legal_person_identifier", useSd) { legalPersonIdentifier },
        claim("legal_name", useSd) { legalName },
        claim("full_powers", useSd) { true },
        claim("effective_from_date", useSd) { iss },
        claim("effective_until_date", useSd) { exp },
        claim("issuance_date", useSd) { iss },
        claim("expiry_date", useSd) { exp },
        claim("issuing_authority", useSd) { issuingAuthority },
        claim("issuing_country", useSd) { issuingCountry },
        claim("issuing_jurisdiction", useSd) { issuingJurisdiction },
        claim("document_number", useSd) { randomIdentifier },
        claim("administrative_number", useSd) { randomIdentifier },
    )

fun OidcUserInfoExtended.buildTaxIdClaims(iss: Instant, exp: Instant, useSd: Boolean) =
    listOfNotNull(
        claim("tax_number", useSd) { randomTaxNumber },
        claim("affiliation_country", useSd) { fallbackAddressCountry },
        claim("registered_given_name", useSd) { userInfo.givenName },
        claim("registered_family_name", useSd) { userInfo.familyName },
        claim("resident_address", useSd) { addressOrRandom.formatted },
        claim("birth_date", useSd) { dateOfBirth },
        claim("church_tax_ID", useSd) { randomChurchTaxId },
        claim("iban", useSd) { fallbackIban },
        claim("pid_id", useSd) { randomPidId },
        claim("issuance_date", useSd) { iss },
        claim("expiry_date", useSd) { exp },
        claim("issuing_authority", useSd) { issuingAuthority },
        claim("issuing_country", useSd) { issuingCountry },
        claim("issuing_jurisdiction", useSd) { issuingJurisdiction },
        claim("document_number", useSd) { randomIdentifier },
        claim("administrative_number", useSd) { randomIdentifier },
    )

fun OidcUserInfoExtended.buildEhicClaims(iss: Instant, exp: Instant, useSd: Boolean): List<ClaimToBeIssued> {
    val issuingAuthorityId = randomIdentifier
    val authenticSourceId = randomIdentifier
    return listOfNotNull(
        claim("issuing_country", useSd) { issuingCountry },
        claim("personal_administrative_number", useSd) { socialSecurityNumber },
        claim("document_number", useSd) { randomIdentifier },
        claim("issuing_authority.id", useSd) { issuingAuthorityId },
        claim("issuing_authority.name", useSd) { issuingAuthority },
        claim("issuing_authority", useSd) {
            listOf(
                claim("id", useSd) { issuingAuthorityId },
                claim("name", useSd) { issuingAuthority }
            )
        },
        claim("authentic_source.id", useSd) { authenticSourceId },
        claim("authentic_source.name", useSd) { authenticSource },
        claim("authentic_source", useSd) {
            listOf(
                claim("id", useSd) { authenticSourceId },
                claim("name", useSd) { authenticSource }
            )
        },
        claim("date_of_issuance", useSd) { iss.toLocalDate() },
        claim("date_of_expiry", useSd) { exp.toLocalDate() },
        claim("starting_date", useSd) { expiryDate },
        claim("ending_date", useSd) { expiryDate },
    )
}

fun OidcUserInfoExtended.buildCorClaims(iss: Instant, exp: Instant, useSd: Boolean) =
    listOfNotNull(
        claim("family_name", useSd) { userInfo.familyName },
        claim("given_name", useSd) { userInfo.givenName },
        claim("birth_date", useSd) { dateOfBirth },
        claim("residence_address", useSd) {
            with(addressOrRandom) {
                listOf(
                    claim("thoroughfare", useSd) { street },
                    claim("locator_designator", useSd) { locator },
                    claim("post_code", useSd) { postCode },
                    claim("post_name", useSd) { city },
                    claim("admin_unit_L1", useSd) { country },
                    claim("admin_unit_L2", useSd) { state },
                    claim("full_address", useSd) { formatted },
                )
            }
        },
        claim("gender", useSd) { gender },
        claim("birth_place", useSd) { randomAddress.city },
        claim("arrival_date", useSd) { arrivalDate },
        claim("nationality", useSd) { nationality },
        claim("issuance_date", useSd) { iss },
        claim("expiry_date", useSd) { exp },
        claim("issuing_authority", useSd) { issuingAuthority },
        claim("document_number", useSd) { randomIdentifier },
        claim("administrative_number", useSd) { randomIdentifier },
        claim("issuing_country", useSd) { issuingCountry },
        claim("issuing_jurisdiction", useSd) { issuingJurisdiction },
    )

fun OidcUserInfoExtended.buildMdlClaims(useSd: Boolean): List<ClaimToBeIssued> {
    val address = addressOrRandom
    return listOfNotNull(
        claim("family_name", useSd) { userInfo.familyName },
        claim("given_name", useSd) { userInfo.givenName },
        claim("birth_date", useSd) { dateOfBirth },
        claim("issue_date", useSd) { issueDate },
        claim("expiry_date", useSd) { expiryDate },
        claim("issuing_country", useSd) { issuingCountry },
        claim("issuing_authority", useSd) { issuingAuthority },
        claim("document_number", useSd) { randomIdentifier },
        claim("portrait", useSd) { portrait },
        claim("driving_privileges", useSd) { arrayOf(fakeDrivingPrivilege) },
        claim("un_distinguishing_sign", useSd) { unDistinguishingSign },
        claim("administrative_number", useSd) { randomIdentifier },
        claim("sex", useSd) { sex },
        claim("height", useSd) { randomHeight },
        claim("weight", useSd) { randomWeight },
        claim("eye_colour", useSd) { randomEyeColour },
        claim("hair_colour", useSd) { randomHairColour },
        claim("birth_place", useSd) { randomAddress.city },
        claim("resident_address", useSd) { address.formatted },
        claim("portrait_capture_date", useSd) { portraitCaptureDate },
        claim("age_in_years", useSd) { ageInYears },
        claim("age_birth_year", useSd) { dateOfBirth.year.toUInt() },
        claim("age_over_12", useSd) { ageOver12 },
        claim("age_over_13", useSd) { ageOver13 },
        claim("age_over_14", useSd) { ageOver14 },
        claim("age_over_16", useSd) { ageOver16 },
        claim("age_over_18", useSd) { ageOver18 },
        claim("age_over_21", useSd) { ageOver21 },
        claim("age_over_25", useSd) { ageOver25 },
        claim("age_over_60", useSd) { ageOver60 },
        claim("age_over_62", useSd) { ageOver62 },
        claim("age_over_65", useSd) { ageOver65 },
        claim("age_over_68", useSd) { ageOver68 },
        claim("nationality", useSd) { nationality },
        claim("resident_city", useSd) { address.city },
        claim("resident_state", useSd) { address.state },
        claim("resident_postal_code", useSd) { address.postCode },
        claim("resident_country", useSd) { address.country },
        claim("family_name_national_character", useSd) { userInfo.familyName + " 🦄" },
        claim("given_name_national_character", useSd) { userInfo.givenName + " 🦄" },
        claim("signature_usual_mark", useSd) { pictureTripleX },
        claim("biometric_template_face", useSd) { pictureTripleX },
        claim("biometric_template_finger", useSd) { pictureTripleX },
        claim("biometric_template_signature_sign", useSd) { pictureTripleX },
        claim("biometric_template_iris", useSd) { pictureTripleX },
    )
}

fun OidcUserInfoExtended.buildAgeClaims(useSd: Boolean) =
    listOfNotNull(
        claim("age_over_12", useSd) { ageOver12 },
        claim("age_over_13", useSd) { ageOver13 },
        claim("age_over_14", useSd) { ageOver14 },
        claim("age_over_16", useSd) { ageOver16 },
        claim("age_over_18", useSd) { ageOver18 },
        claim("age_over_21", useSd) { ageOver21 },
        claim("age_over_25", useSd) { ageOver25 },
        claim("age_over_60", useSd) { ageOver60 },
        claim("age_over_62", useSd) { ageOver62 },
        claim("age_over_65", useSd) { ageOver65 },
        claim("age_over_68", useSd) { ageOver68 },
    )

private fun claim(key: String, useSd: Boolean, value: () -> Any?): ClaimToBeIssued? =
    value()?.let { ClaimToBeIssued(key, it, useSd) }
