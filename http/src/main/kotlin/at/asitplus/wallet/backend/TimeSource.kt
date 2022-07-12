package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.FixedTimeClock
import at.asitplus.wallet.lib.agent.MonthAndDay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.Month
import java.time.Year

enum class TimeSource {
    SYSTEM,
    TEST
}

object TestTimeSource {
    val schoolYear = 2021
    val javaSchoolYear: Year = Year.of(schoolYear)
    val clock: Clock =
        FixedTimeClock(Instant.parse("$schoolYear-10-11T00:00:00.000Z").toEpochMilliseconds())
    val scholYearStart: MonthAndDay = Month.SEPTEMBER to 1u



    fun now() = clock.now()
}