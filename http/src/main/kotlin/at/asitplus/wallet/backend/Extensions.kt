package at.asitplus.wallet.backend

import org.springframework.web.util.UriComponentsBuilder
import java.time.Instant

object Extensions {

    fun appendPath(url: String, vararg path: String) =
        UriComponentsBuilder.fromHttpUrl(url).pathSegment(*path).toUriString()

    val Int.daysToSeconds get() = this * 24L * 60L * 60L

    /**
     * One cannot simply subtract days from an [Instant],
     * therefore we subtract seconds, which is close enough for our purposes
     */
    fun InstantNowMinusDays(days: Int): Instant = Instant.now().minusSeconds(days.daysToSeconds)

    /**
     * One cannot simply add days to an [Instant],
     * therefore we add seconds, which is close enough for our purposes
     */
    fun InstantNowPlusDays(days: Int): Instant = Instant.now().plusSeconds(days.daysToSeconds)

}