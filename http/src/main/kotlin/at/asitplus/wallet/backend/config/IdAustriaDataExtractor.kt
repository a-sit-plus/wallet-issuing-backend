package at.asitplus.wallet.backend.config

import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.wallet.eupid.IsoIec5218Gender
import at.asitplus.wallet.mdl.IsoSexEnum
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.Charset
import kotlin.random.Random

fun OidcUserInfoExtended.parseIdAustriaAddress(): Address? =
    getClaimAsString("urn:eidgvat:attributes.mainAddress")?.let { idaAddress ->
        runCatching {
            idaAddress.parseIdAustriaAddress()
        }.getOrNull()
    }

private fun String.parseIdAustriaAddress(): Address? {
    val json = Json.parseToJsonElement(
        decodeToByteArray(Base64()).toString(Charset.defaultCharset())
    ) as? JsonObject
    val postCode = json.getPrimitiveContent("Postleitzahl")
    val city = json.getPrimitiveContent("Ortschaft")
    val street = json.getPrimitiveContent("Strasse")
    val locator = json.getPrimitiveContent("Hausnummer")
    return if (postCode != null && city != null && street != null && locator != null) {
        Address(
            postCode = postCode,
            city = city,
            state = postCode.toState(),
            street = street,
            locator = locator.toIntOrNull() ?: Random.nextInt(1, 99)
        )
    } else {
        null
    }
}

private fun JsonObject?.getPrimitiveContent(key: String) = (this?.get(key) as? JsonPrimitive)?.content

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
        ?: "ACME Inc."

val OidcUserInfoExtended.legalPersonIdentifier: String
    get() = getClaimAsString("urn:pvpgvat:oidc.mandator_legal_person_source_pin")
        ?: "ZMK+437842q"

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
