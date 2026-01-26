package at.asitplus.wallet.backend.config

import at.asitplus.openid.OidcUserInfoExtended
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

val OidcUserInfoExtended.ageOver12
    get() = loadAgeOver(12)

val OidcUserInfoExtended.ageOver13
    get() = loadAgeOver(13)

val OidcUserInfoExtended.ageOver14
    get() = loadAgeOver(14)

val OidcUserInfoExtended.ageOver16
    get() = loadAgeOver(16)

val OidcUserInfoExtended.ageOver18: Boolean
    get() = userInfo.ageOver18 ?: loadAgeOver(18)

val OidcUserInfoExtended.ageOver21: Boolean
    get() = loadAgeOver(21)

val OidcUserInfoExtended.ageOver25: Boolean
    get() = loadAgeOver(25)

val OidcUserInfoExtended.ageOver60: Boolean
    get() = loadAgeOver(60)

val OidcUserInfoExtended.ageOver62: Boolean
    get() = loadAgeOver(62)

val OidcUserInfoExtended.ageOver65: Boolean
    get() = loadAgeOver(65)

val OidcUserInfoExtended.ageOver68: Boolean
    get() = loadAgeOver(68)

private fun OidcUserInfoExtended.loadAgeOver(age: Int): Boolean =
    (getClaimAsString("org.iso.18013.5.1:age_over_$age")?.toBoolean()
        ?: (dateOfBirth < Clock.System.now().toLocalDate().minus(DatePeriod(age))))

fun Instant.toLocalDate() = toLocalDateTime(TimeZone.currentSystemDefault()).date

val OidcUserInfoExtended.ageInYears: UInt
    get() = (Clock.System.now().toLocalDate().minus(dateOfBirth)).years.toUInt()

val OidcUserInfoExtended.portrait: ByteArray?
    get() = userInfo.picture?.decodeToByteArray(Base64())
        ?: getClaimAsString("org.iso.18013.5.1:portrait")?.decodeToByteArray(Base64())

val OidcUserInfoExtended.portraitCaptureDate: LocalDate
    get() = getClaimAsString("org.iso.18013.5.1:portrait_capture_date")
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate(2020, Random.nextInt(1, 12), Random.nextInt(1, 28))
