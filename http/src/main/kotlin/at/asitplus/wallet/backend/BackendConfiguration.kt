package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.ApiKeyAuthnService
import at.asitplus.wallet.backend.auth.DebugExtNonceAuthnService
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.backend.auth.NoopExtNonceAuthnService
import at.asitplus.wallet.backend.auth.SimpleApiKeyAuthnService
import at.asitplus.wallet.backend.data.DatabaseDeviceBindingStorageService
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.data.PupilIdCredentialStoreAdapter
import at.asitplus.wallet.lib.DefaultKeyIdService
import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.DefaultCryptoService
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.agent.MessageWrapper
import at.asitplus.wallet.lib.data.ConstantIndex
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.ResourcePatternResolver
import kotlin.time.Duration.Companion.minutes

@Configuration
@EnableConfigurationProperties(value = [BackendConfigurationProperties::class])
class BackendConfiguration {

    @Autowired
    private lateinit var configurationProperties: BackendConfigurationProperties

    @Autowired
    private lateinit var resourceLoader: ResourceLoader

    @Autowired
    private lateinit var resourcePatternResolver: ResourcePatternResolver

    init {
        Napier.base(DebugAntilog())
    }

    @Bean
    fun extNonceAuthnService(): ExtNonceAuthnService {
        if (configurationProperties.debug.enabled) {
            return DebugExtNonceAuthnService(
                SimpleChallengeService(
                    lifetimeSeconds = configurationProperties.authn.challengeTimeoutSeconds
                )
            )
        } else {
            return NoopExtNonceAuthnService()
        }
    }

    @Bean
    fun apiKeyAuthnService(): ApiKeyAuthnService {
        return SimpleApiKeyAuthnService(configurationProperties.authn)
    }

    @Bean
    fun certificateService(): CertificateService {
        return InMemoryCertificateService()
    }

    @Bean
    fun challengeService(): ChallengeService {
        return SimpleChallengeService(lifetimeSeconds = configurationProperties.authn.challengeTimeoutSeconds)
    }

    @Bean
    fun deviceBindingStorageService(deviceBindingRepository: DeviceBindingRepository): DeviceBindingStorageService {
        return DatabaseDeviceBindingStorageService(deviceBindingRepository)
    }

    @Bean
    fun pupilIdService(
        issueCredentialMessengerPupilId: IssueCredentialMessenger,
    ): PupilIdService {
        return DefaultPupilIdService(issueCredentialMessengerPupilId)
    }

    @Bean
    fun pupilIdRevocationService(
        credentialRepo: IssuedCredentialRepository,
        deviceBindingStorageService: DeviceBindingStorageService,
    ): PupilIdRevocationService {
        return DefaultPupilIdRevocationService(credentialRepo, deviceBindingStorageService)
    }

    @Bean
    fun deviceBindingAuthnService(
        deviceBindingStorageService: DeviceBindingStorageService,
        deviceBindingAuthnChallengeService: ChallengeService,
    ): DeviceBindingAuthnService {
        return SimpleDeviceBindingAuthnService(deviceBindingStorageService, deviceBindingAuthnChallengeService)
    }

    @Bean
    fun issuerCredentialStore(
        pupilIdRevocationService: PupilIdRevocationService,
    ): PupilIdCredentialStoreAdapter {
        return PupilIdCredentialStoreAdapter(pupilIdRevocationService)
    }

    @Bean
    fun issuerCredentialRandomDataProvider(): IssuerCredentialDataProvider {
        val mapOfPhotos =
            resourcePatternResolver.getResources(configurationProperties.randomPhotoLocation.toString() + "/*.jpg")
                .filter { it.exists() }
                .filter { it.filename != null }
                .map { it.filename!! to it.inputStream }
                .map { it.first to it.second.readAllBytes() }
        return RandomCredentialDataProvider(
            configurationProperties.credentialLifetime.toMinutes().minutes,
            mapOfPhotos.toMap()
        )
    }

    @Bean
    fun issuerCryptoService() = when (configurationProperties.issuerKey.type) {
        KeyType.FILE -> FileCryptoService(
            configurationProperties.issuerKey.file!!,
            resourceLoader,
            DefaultKeyIdService()
        )
        KeyType.MEMORY -> DefaultCryptoService()
    }

    @Bean
    fun issuerAgent(
        issuerCredentialStore: IssuerCredentialStore,
        issuerCredentialDataProvider: IssuerCredentialDataProvider,
        issuerCryptoService: CryptoService
    ): Agent {
        return Agent(
            cryptoService = issuerCryptoService,
            issuerCredentialStore = issuerCredentialStore,
            dataProvider = issuerCredentialDataProvider,
            revocationListUrl = "${configurationProperties.publicContext}/credentials/status/1",
        )
    }

    @Bean
    fun issuerMessageWrapper(issuerAgent: Agent): MessageWrapper {
        return MessageWrapper(issuerAgent.cryptoService)
    }

    @Bean
    fun issueCredentialMessengerPupilId(
        issuerAgent: Agent,
        issuerMessageWrapper: MessageWrapper
    ): IssueCredentialMessenger {
        return IssueCredentialMessenger(
            issuerAgent,
            issuerMessageWrapper,
            "${configurationProperties.publicContext}/issue",
            true,
            credentialScheme = ConstantIndex.PupilId,
        )
    }

}