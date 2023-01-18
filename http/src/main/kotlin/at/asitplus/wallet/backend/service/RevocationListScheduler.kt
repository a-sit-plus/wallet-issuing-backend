package at.asitplus.wallet.backend.service

import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.lib.agent.TimePeriodProvider
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds
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
            Clock.System.now().plus(5.seconds).toJavaInstant(),
            configurationProperties.revocationList.dirtyCheckRateDuration.toJavaDuration()
        )
        taskScheduler.scheduleAtFixedRate(
            { writeRegularRevocationList() },
            Clock.System.now().plus(5.seconds).toJavaInstant(),
            configurationProperties.revocationList.regularCheckRateDuration.toJavaDuration()
        )
    }

    override fun onApplicationEvent(event: RevocationEvent) {
        log.debug("onApplicationEvent $event")
        mapTimePeriodDirty[event.timePeriod] = true
        if (!mapTimePeriodTimestamp.containsKey(event.timePeriod))
            mapTimePeriodTimestamp[event.timePeriod] = Instant.fromEpochSeconds(0)
    }

    fun writeDirtyRevocationList() {
        mapTimePeriodDirty
            .filterValues { it }
            .forEach {
                log.debug("writeDirtyRevocationList for ${it.key}")
                thread {
                    revocationListWriter.writeRevocationList(it.key)
                }
                mapTimePeriodTimestamp[it.key] = Clock.System.now()
            }
    }

    fun writeRegularRevocationList() {
        mapTimePeriodTimestamp
            .filterValues { Clock.System.now() - it > configurationProperties.revocationList.regularWriteTimeoutDuration }
            .forEach {
                log.debug("writeRegularRevocationList for ${it.key}")
                thread {
                    revocationListWriter.writeRevocationList(it.key)
                }
                mapTimePeriodTimestamp[it.key] = Clock.System.now()
            }
    }

}