package at.asitplus.wallet.backend.service

import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.lib.agent.TimePeriodProvider
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct
import kotlin.time.toJavaDuration


@Service
class RevocationListScheduler(
    private val revocationListWriter: RevocationListWriter,
    private val timePeriodProvider: TimePeriodProvider,
    private val configurationProperties: BackendConfigurationProperties,
    private val taskScheduler: TaskScheduler,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val mapTimePeriodDirty = mutableMapOf<Int, Boolean>()
    private val mapTimePeriodTimestamp = mutableMapOf<Int, Instant>()

    @PostConstruct
    fun postConstruct() {
        log.debug("postConstruct")
        timePeriodProvider.getRelevantTimePeriods(Clock.System).forEach {
            mapTimePeriodDirty[it] = false
            mapTimePeriodTimestamp[it] = Instant.fromEpochSeconds(0)
        }
    }

    @EventListener
    fun onRevocationEvent(event: RevocationEvent) {
        log.debug("onRevocationEvent $event")
        mapTimePeriodDirty[event.timePeriod] = true
        if (!mapTimePeriodTimestamp.containsKey(event.timePeriod))
            mapTimePeriodTimestamp[event.timePeriod] = Instant.fromEpochSeconds(0)
    }

    @EventListener
    fun onApplicationStartedEvent(event: ApplicationStartedEvent) {
        log.debug("onApplicationStartedEvent $event")
        taskScheduler.scheduleAtFixedRate(
            { writeDirtyRevocationList() },
            configurationProperties.revocationList.dirtyCheckRateDuration.toJavaDuration()
        )
        taskScheduler.scheduleAtFixedRate(
            { writeRegularRevocationList() },
            configurationProperties.revocationList.regularCheckRateDuration.toJavaDuration()
        )
    }

    fun writeDirtyRevocationList() {
        mapTimePeriodDirty
            .filterValues { it }
            .forEach {
                log.debug("writeDirtyRevocationList for ${it.key}")
                revocationListWriter.writeRevocationList(it.key)
                mapTimePeriodTimestamp[it.key] = Clock.System.now()
                mapTimePeriodDirty[it.key] = false
            }
    }

    fun writeRegularRevocationList() {
        mapTimePeriodTimestamp
            .filterValues { it.isOutdated }
            .forEach {
                log.debug("writeRegularRevocationList for ${it.key}")
                revocationListWriter.writeRevocationList(it.key)
                mapTimePeriodTimestamp[it.key] = Clock.System.now()
            }
    }

    private val Instant.isOutdated: Boolean
        get() {
            return (Clock.System.now() - this) > (configurationProperties.revocationList.regularWriteTimeoutDuration - configurationProperties.revocationList.regularCheckRateDuration)
        }
}