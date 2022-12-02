package at.asitplus.wallet.backend.data

import kotlinx.datetime.Instant

object LenientInstantParser {

    private const val yearMonthDateLength = "yyyy-MM-dd".length

    fun parse(it: String): Instant? {
        runCatching {
            return Instant.parse(it)
        }.onFailure { _ ->
            runCatching {
                return Instant.parse(it + "Z")
            }.onFailure {
                return null
            }
        }
        return null
    }


    fun toYearMonthDateString(cappedExpiration: Instant): String {
        return if (cappedExpiration.toString().length >= yearMonthDateLength) cappedExpiration.toString()
            .substring(0, yearMonthDateLength) else cappedExpiration.toString()
    }

}