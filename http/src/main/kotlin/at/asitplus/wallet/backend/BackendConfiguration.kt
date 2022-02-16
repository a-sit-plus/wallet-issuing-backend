package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.DummyNonceToBpkService
import at.asitplus.wallet.backend.auth.NonceToBpkService
import at.asitplus.wallet.backend.data.DatabaseDeviceBindingStorageService
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.backend.model.IdentifierRegistry
import at.asitplus.wallet.backend.model.IdentifierRepository
import at.asitplus.wallet.lib.DefaultKeyIdService
import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.DefaultCryptoService
import at.asitplus.wallet.lib.agent.DelegatingProtocolMessenger
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.agent.MessageWrapper
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.encodeBase64
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
        //Napier.base(DebugAntilog())
    }

    @Bean
    fun nonceToBpkService(): NonceToBpkService {
        return DummyNonceToBpkService()
    }

    @Bean
    fun certificateService(): CertificateService {
        return InMemoryCertificateService()
    }

    @Bean
    fun challengeService(): ChallengeService {
        return SimpleChallengeService()
    }

    @Bean
    fun deviceBindingStorageService(deviceBindingRepository: DeviceBindingRepository): DeviceBindingStorageService {
        return DatabaseDeviceBindingStorageService(deviceBindingRepository)
    }

    @Bean
    fun pupilIdService(
        issueCredentialMessengerPupilId: IssueCredentialMessenger,
        deviceBindingStorageService: DeviceBindingStorageService
    ): PupilIdService {
        return DefaultPupilIdService(issueCredentialMessengerPupilId, deviceBindingStorageService)
    }

    @Bean
    fun deviceBindingResponseValidator(
        deviceBindingStorageService: DeviceBindingStorageService,
        deviceBindingAuthnChallengeService: ChallengeService,
    ): DeviceBindingResponseValidator {
        return SimpleDeviceBindingResponseValidator(deviceBindingStorageService, deviceBindingAuthnChallengeService)
    }

    @Bean
    fun identifierRegistry(@Autowired identifierRepository: IdentifierRepository): IdentifierRegistry {
        return IdentifierRegistry(identifierRepository)
    }

    @Bean
    fun issuerCredentialRandomDataProvider(): IssuerCredentialDataProvider {
        val mapOfPhotos =
            resourcePatternResolver.getResources(configurationProperties.randomPhotoLocation.toString() + "/*.jpg")
                .filter { it.exists() }
                .filter { it.filename != null }
                .map { it.filename!! to it.inputStream }
                .map { it.first to it.second.readAllBytes() }
                .map { it.first to it.second.encodeBase64() }
        return MvpRandomDataProvider(
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
        @Autowired identifierRegistry: IdentifierRegistry,
        @Autowired issuerCredentialDataProvider: IssuerCredentialDataProvider,
        @Autowired issuerCryptoService: CryptoService
    ): Agent {
        return Agent(
            cryptoService = issuerCryptoService,
            issuerCredentialStore = identifierRegistry,
            dataProvider = issuerCredentialDataProvider,
            revocationListUrl = "${configurationProperties.publicContext}/credentials/status/1",
        )
    }

    @Bean
    fun issuerMessageWrapper(@Autowired issuerAgent: Agent): MessageWrapper {
        return MessageWrapper(issuerAgent.cryptoService)
    }

    @Bean
    fun issueCredentialMessengerPupilId(
        @Autowired issuerAgent: Agent,
        @Autowired issuerMessageWrapper: MessageWrapper
    ): IssueCredentialMessenger {
        return IssueCredentialMessenger(
            issuerAgent,
            issuerMessageWrapper,
            "${configurationProperties.publicContext}/issue",
            true,
            credentialScheme = ConstantIndex.PupilId,
        )
    }

    @Bean
    fun issueCredentialMessengerGreenPass(
        @Autowired issuerAgent: Agent,
        @Autowired issuerMessageWrapper: MessageWrapper
    ): IssueCredentialMessenger {
        return IssueCredentialMessenger(
            issuerAgent,
            issuerMessageWrapper,
            "${configurationProperties.publicContext}/issue",
            true,
            credentialScheme = ConstantIndex.GreenPass,
        )

    }

    @Bean
    fun delegatingProtocolMessenger(
        @Autowired issueCredentialMessengerPupilId: IssueCredentialMessenger,
        @Autowired issueCredentialMessengerGreenPass: IssueCredentialMessenger,
    ): DelegatingProtocolMessenger {
        return DelegatingProtocolMessenger(listOf(issueCredentialMessengerPupilId, issueCredentialMessengerGreenPass))
    }

}