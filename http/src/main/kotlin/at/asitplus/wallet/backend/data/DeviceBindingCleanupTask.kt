package at.asitplus.wallet.backend.data

import at.asitplus.wallet.backend.BackendConfigurationProperties
import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.backend.Extensions.daysToSeconds
import at.asitplus.wallet.backend.RevocationService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant

@ConditionalOnProperty("\${backend.cleanup.enabled}")
@Service
class DeviceBindingCleanupTask(
    private val deviceBindingStorageService: DeviceBindingStorageService,
    private val revocationService: RevocationService,
    private val configuration: BackendConfigurationProperties,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Scheduled(fixedRateString = "\${backend.cleanup.bindings-scheduling-rate:PT24H}", initialDelay = 1000L)
    fun runBindingCleanup() {
        if (!configuration.cleanup.enabled) return
        log.info("Running device binding cleanup")
        val now = Instant.now()
        val cutoff = now.minusSeconds(configuration.cleanup.bindingsExpirationDays.daysToSeconds)
        val count = deviceBindingStorageService.deleteExpiredBefore(cutoff)
        log.info("Removed {} bindings expired before {}", count, cutoff)
    }

    @Scheduled(fixedRateString = "\${backend.cleanup.credentials-scheduling-rate:PT24H}", initialDelay = 1000L)
    fun runCredentialCleanup() {
        if (!configuration.cleanup.enabled) return
        log.info("Running credentials cleanup")
        val now = Instant.now()
        val cutoff = now.minusSeconds(configuration.cleanup.credentialsExpirationDays.daysToSeconds)
        val count = revocationService.deleteExpiredCredentialsBefore(cutoff)
        log.info("Removed {} credentials expired before {}", count, cutoff)
    }

}
