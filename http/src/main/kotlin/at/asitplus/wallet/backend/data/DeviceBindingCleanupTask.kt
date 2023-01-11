package at.asitplus.wallet.backend.data

import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.service.DeviceBindingStorageService
import at.asitplus.wallet.backend.service.RevocationService
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import kotlin.time.Duration.Companion.days

@ConditionalOnProperty("\${backend.cleanup.enabled}")
@Service
class DeviceBindingCleanupTask(
    private val deviceBindingStorageService: DeviceBindingStorageService,
    private val revocationService: RevocationService,
    private val configuration: BackendConfigurationProperties,
) {

    @Scheduled(
        fixedRateString = "\${backend.cleanup.bindings-scheduling-rate:PT24H}",
        initialDelay = 1000L
    )
    fun runBindingCleanup() {
        if (!configuration.cleanup.enabled) return
        Napier.i("Running device binding cleanup")
        val cutoff = Clock.System.now() - configuration.cleanup.bindingsExpirationDays.days
        val count = deviceBindingStorageService.deleteExpiredBefore(cutoff)
        Napier.i("Removed $count bindings expired before $cutoff")
    }

    @Scheduled(
        fixedRateString = "\${backend.cleanup.credentials-scheduling-rate:PT24H}",
        initialDelay = 1000L
    )
    fun runCredentialCleanup() {
        if (!configuration.cleanup.enabled) return
        Napier.i("Running credentials cleanup")
        val cutoff = Clock.System.now() + configuration.cleanup.credentialsExpirationDays.days
        val count = revocationService.deleteExpiredCredentialsBefore(cutoff)
        Napier.i("Removed $count credentials expired before $cutoff")
    }

}
