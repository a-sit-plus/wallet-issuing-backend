package at.asitplus.wallet.backend.config

import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.wallet.mdl.DrivingPrivilege
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.serialization.json.JsonPrimitive
import java.util.*
import kotlin.random.Random
import kotlin.random.nextUInt

val OidcUserInfoExtended.fakeDrivingPrivilege
    get() = DrivingPrivilege(
        vehicleCategoryCode = "B",
        issueDate = issueDate,
        expiryDate = expiryDate,
    )

val OidcUserInfoExtended.dateOfBirth: LocalDate
    get() = userInfo.birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: randomDateOfBirth

val OidcUserInfoExtended.email
    get() = userInfo.email
        ?: "info@example.com"

val OidcUserInfoExtended.phoneNumber
    get() = userInfo.phoneNumber?.replace("-", "")
        ?: "+498999998001"

fun OidcUserInfoExtended.getClaimAsString(key: String): String? {
    val element = jsonObject[key]
    if (element is JsonPrimitive) {
        return element.content
    }
    return element?.toString()
}

val OidcUserInfoExtended.socialSecurityNumber: String
    get() = "1111" + dateOfBirth.format(LocalDate.Format {
        day()
        monthNumber()
        year()
    })

val randomIdentifier: String
    get() = UUID.randomUUID().toString()
val expiryDate: LocalDate
    get() = LocalDate.parse("2026-12-31")
val issueDate: LocalDate
    get() = LocalDate.parse("2023-01-01")
val trustAnchor: String
    get() = "https://wallet.a-sit.at/"
val issuingCountry: String
    get() = "AT"
val issuingJurisdiction: String
    get() = "AT-0"
val issuingAuthority: String
    get() = "Miniwahr"
val authenticSource: String
    get() = "Ministry of Love"
val unDistinguishingSign: String
    get() = "A"
val fallbackBirthCountry: String
    get() = "AT"
val fallbackAddressCountry: String
    get() = "AT"
val randomWeight: UInt
    get() = Random.nextUInt(60u, 120u)
val randomHeight: UInt
    get() = Random.nextUInt(150u, 210u)
val randomEyeColour
    get() = listOf(
        "black",
        "blue",
        "brown",
        "dichromatic",
        "grey",
        "green",
        "hazel",
        "maroon",
        "pink",
        "unknown"
    ).random()

val randomHairColour
    get() = listOf(
        "bald",
        "black",
        "blond",
        "brown",
        "grey",
        "red",
        "auburn",
        "sandy",
        "white",
        "unknown"
    ).random()

data class Address(val postCode: String, val city: String, val state: String, val street: String, val locator: Int)

val OidcUserInfoExtended.randomAddress: Address
    get() = listOf(
        Address("6900", "Bregenz", "Vorarlberg", randomStreet, randomAddressLocator),
        Address("6010", "Innsbruck", "Tirol", randomStreet, randomAddressLocator),
        Address("5010", "Salzburg", "Salzburg", randomStreet, randomAddressLocator),
        Address("4020", "Linz", "Oberösterreich", randomStreet, randomAddressLocator),
        Address("3100", "St. Pölten", "Niederösterreich", randomStreet, randomAddressLocator),
        Address("1010", "Wien", "Wien", randomStreet, randomAddressLocator),
        Address("8010", "Graz", "Steiermark", randomStreet, randomAddressLocator),
        Address("7000", "Eisenstadt", "Burgenland", randomStreet, randomAddressLocator),
        Address("9020", "Klagenfurt", "Kärnten", randomStreet, randomAddressLocator)
    ).random()

val randomAddressLocator
    get() = Random.nextInt(1, 99)

val randomStreet
    get() = listOf("Hauptstraße", "Herrengasse", "Hauptplatz", "Landstraße", "Dorfstraße").random()

val randomDateOfBirth
    get() = LocalDate(Random.nextInt(1970, 2000), Random.nextInt(1, 12), Random.nextInt(1, 28))

val pictureTripleX
    get() = """
    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
    vx2fPC3+/wCv9sLW1/B9vH//2Q==
""".trimIndent().decodeToByteArray(Base64())
