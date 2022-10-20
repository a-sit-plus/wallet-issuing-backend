package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.TestTimeSource
import at.asitplus.wallet.backend.data.*
import io.kotest.matchers.collections.shouldBeEmpty
import kotlinx.datetime.toJavaInstant
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import kotlin.time.Duration.Companion.days

@SpringBootTest(properties = ["backend.cleanup.enabled=true", "backend.authn.device-binding.attestation.noop=true"])
class AutomaticCleanupTest {

    @SpyBean
    private lateinit var deviceBindingCleanupTask: DeviceBindingCleanupTask

    @Autowired
    private lateinit var deviceBindingRepository: DeviceBindingRepository

    @Autowired
    private lateinit var credentialRepository: IssuedCredentialRepository

    @Autowired
    private lateinit var configuration: BackendConfigurationProperties

    @Test
    fun `should be called`() {
        deviceBindingRepository.deleteAll()
        credentialRepository.deleteAll()

        val validUntilBinding =
            TestTimeSource.now() + (configuration.cleanup.bindingsExpirationDays + 1).days
        val deviceBinding =
            DeviceBinding("bpk", byteArrayOf(), "deviceName", "deviceId", validUntilBinding.toJavaInstant())
                .also { deviceBindingRepository.save(it) }
        val validUntilCredential =
            TestTimeSource.now() - (configuration.cleanup.credentialsExpirationDays + 1).days
        IssuedCredential(
            "vcId",
            "subjectId",
            validUntilCredential.toJavaInstant(),
            TestTimeSource.timePeriod,
            deviceBinding,
            "attributeName",
            1L
        ).also { credentialRepository.save(it) }

        Thread.sleep(2000L)

        verify(deviceBindingCleanupTask).runBindingCleanup()
        verify(deviceBindingCleanupTask).runCredentialCleanup()

        credentialRepository.findAll().shouldBeEmpty()
        deviceBindingRepository.findAll().shouldBeEmpty()
    }

}