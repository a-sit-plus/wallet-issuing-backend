package at.asitplus.wallet.backend

import java.time.Year


val TestTimeSource.javatimePeriod: Year
    get() = Year.of(timePeriod)