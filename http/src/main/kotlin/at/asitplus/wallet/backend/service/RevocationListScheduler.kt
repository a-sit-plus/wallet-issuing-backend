package at.asitplus.wallet.backend.service

import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.config.RevocationListConfigurationProperties
import at.asitplus.wallet.lib.agent.TimePeriodProvider
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toJavaDuration


@Service
class RevocationListScheduler(
    private val revocationListWriter: RevocationListWriter,
    private val timePeriodProvider: TimePeriodProvider,
    private val configurationProperties: BackendConfigurationProperties,
    private val taskScheduler: TaskScheduler,
    private val schedulerScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
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
        log.debug("onRevocationEvent {}", event)
        mapTimePeriodDirty[event.timePeriod] = true
        if (!mapTimePeriodTimestamp.containsKey(event.timePeriod))
            mapTimePeriodTimestamp[event.timePeriod] = Instant.fromEpochSeconds(0)
    }

    @EventListener
    fun onApplicationStartedEvent(event: ApplicationStartedEvent) {
        log.debug("onApplicationStartedEvent {}", event)
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
            .forEach { (timePeriod, _) ->
                log.debug("writeDirtyRevocationList for $timePeriod")
                schedulerScope.launch {
                    revocationListWriter.writeRevocationList(timePeriod)
                }
                mapTimePeriodTimestamp[timePeriod] = Clock.System.now()
                mapTimePeriodDirty[timePeriod] = false
            }
    }

    fun writeRegularRevocationList() {
        mapTimePeriodTimestamp
            .filterValues { it.isOutdated }
            .forEach { (timePeriod, _) ->
                log.debug("writeRegularRevocationList for $timePeriod")
                schedulerScope.launch {
                    revocationListWriter.writeRevocationList(timePeriod)
                }
                mapTimePeriodTimestamp[timePeriod] = Clock.System.now()
            }
    }

    private val Instant.isOutdated: Boolean
        get() {
            return (Clock.System.now() - this) > configurationProperties.revocationList.outdatedDuration()
        }

    private fun RevocationListConfigurationProperties.outdatedDuration(): Duration =
        (regularWriteTimeoutDuration - regularCheckRateDuration)
}