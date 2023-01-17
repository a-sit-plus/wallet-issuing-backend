package at.asitplus.wallet.backend.service

import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.lib.agent.TimePeriodProvider
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct
import kotlin.time.toJavaDuration

@Service
class RevocationListScheduler(
    private val revocationListWriter: RevocationListWriter,
    private val timePeriodProvider: TimePeriodProvider,
    private val configurationProperties: BackendConfigurationProperties,
    private val taskScheduler: TaskScheduler,
) : ApplicationListener<RevocationEvent> {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val mapTimePeriodDirty = mutableMapOf<Int, Boolean>()
    private val mapTimePeriodTimestamp = mutableMapOf<Int, Instant>()

    @PostConstruct
    fun postConstruct() {
        timePeriodProvider.getRelevantTimePeriods(Clock.System).forEach {
            mapTimePeriodDirty[it] = false
            mapTimePeriodTimestamp[it] = Instant.fromEpochSeconds(0)
        }
        taskScheduler.scheduleAtFixedRate(
            { writeDirtyRevocationList() },
            configurationProperties.revocationList.dirtyCheckRateDuration.toJavaDuration()
        )
        taskScheduler.scheduleAtFixedRate(
            { writeRegularRevocationList() },
            configurationProperties.revocationList.regularCheckRateDuration.toJavaDuration()
        )
    }

    override fun onApplicationEvent(event: RevocationEvent) {
        log.debug("onApplicationEvent $event")
        mapTimePeriodDirty[event.timePeriod] = true
    }

    fun writeDirtyRevocationList() {
        mapTimePeriodDirty
            .filterValues { it }
            .forEach {
                log.debug("writeDirtyRevocationList for ${it.key}")
                revocationListWriter.writeRevocationList(it.key)
                mapTimePeriodTimestamp[it.key] = Clock.System.now()
            }
    }

    fun writeRegularRevocationList() {
        mapTimePeriodTimestamp
            .filterValues { Clock.System.now() - it > configurationProperties.revocationList.regularWriteTimeoutDuration }
            .forEach {
                log.debug("writeRegularRevocationList for ${it.key}")
                revocationListWriter.writeRevocationList(it.key)
                mapTimePeriodTimestamp[it.key] = Clock.System.now()
            }
    }

}