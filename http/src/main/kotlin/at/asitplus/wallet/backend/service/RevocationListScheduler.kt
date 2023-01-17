package at.asitplus.wallet.backend.service

import at.asitplus.wallet.lib.agent.TimePeriodProvider
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct
import kotlin.time.Duration.Companion.days

@Service
class RevocationListScheduler(
    private val revocationListWriter: RevocationListWriter,
    private val timePeriodProvider: TimePeriodProvider,
) :
    ApplicationListener<RevocationEvent> {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val mapTimePeriodDirty = mutableMapOf<Int, Boolean>()
    private val mapTimePeriodTimestamp = mutableMapOf<Int, Instant>()

    @PostConstruct
    fun postConstruct() {
        timePeriodProvider.getRelevantTimePeriods(Clock.System).forEach {
            mapTimePeriodDirty[it] = false
            mapTimePeriodTimestamp[it] = Instant.fromEpochSeconds(0)
        }
    }

    override fun onApplicationEvent(event: RevocationEvent) {
        log.debug("onApplicationEvent $event")
        mapTimePeriodDirty[event.timePeriod] = true
    }

    @Scheduled
    fun writeDirtyRevocationList() {
        mapTimePeriodDirty
            .filterValues { it }
            .forEach {
                log.debug("writeDirtyRevocationList for ${it.key}")
                revocationListWriter.writeRevocationList(it.key)
                mapTimePeriodTimestamp[it.key] = Clock.System.now()
            }
    }

    @Scheduled
    fun writeRegularRevocationList() {
        val durationAfterWhichToWriteRevocationList = 5.days
        mapTimePeriodTimestamp
            .filterValues { Clock.System.now() - it > durationAfterWhichToWriteRevocationList }
            .forEach {
                log.debug("writeRegularRevocationList for ${it.key}")
                revocationListWriter.writeRevocationList(it.key)
                mapTimePeriodTimestamp[it.key] = Clock.System.now()
            }
    }

}