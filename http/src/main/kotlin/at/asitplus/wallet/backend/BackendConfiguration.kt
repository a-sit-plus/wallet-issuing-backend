package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.DummyNonceToBpkService
import at.asitplus.wallet.backend.auth.NonceToBpkService
import at.asitplus.wallet.backend.model.IdentifierRegistry
import at.asitplus.wallet.backend.model.IdentifierRepository
import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.DelegatingProtocolMessenger
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.agent.MessageWrapper
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.encodeBase64
import at.asitplus.wallet.lib.jvm.BitSetAdapterJvm
import at.asitplus.wallet.lib.jvm.InMemoryCryptoServiceJvm
import at.asitplus.wallet.lib.jvm.JwsServiceJvm
import at.asitplus.wallet.lib.jvm.KeyIdServiceJvm
import at.asitplus.wallet.lib.jvm.ValidatorJvm
import at.asitplus.wallet.lib.jvm.ZlibServiceJvm
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
    fun deviceBindingStorageService(): DeviceBindingStorageService {
        return InMemoryDeviceBindingStorageService()
    }

    @Bean
    fun pupilIdService(@Autowired issueCredentialMessengerPupilId: IssueCredentialMessenger): PupilIdService {
        return DefaultPupilIdService(issueCredentialMessengerPupilId)
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
        return IssuerCredentialRandomDataProvider(
            configurationProperties.credentialLifetime.toMinutes().minutes,
            mapOfPhotos.toMap()
        )
    }

    @Bean
    fun issuerCryptoService() = when (configurationProperties.issuerKey.type) {
        KeyType.FILE -> FileCryptoService(configurationProperties.issuerKey.file!!, resourceLoader, KeyIdServiceJvm())
        KeyType.MEMORY -> InMemoryCryptoServiceJvm()
    }

    @Bean
    fun issuerAgent(
        @Autowired identifierRegistry: IdentifierRegistry,
        @Autowired issuerCredentialDataProvider: IssuerCredentialDataProvider,
        @Autowired issuerCryptoService: CryptoService
    ): Agent {
        return Agent(
            validator = ValidatorJvm.new(),
            cryptoService = issuerCryptoService,
            issuerCredentialStore = identifierRegistry,
            dataProvider = issuerCredentialDataProvider,
            revocationListUrl = "${configurationProperties.publicContext}/credentials/status/1",
            zlibService = ZlibServiceJvm(),
            bitSetAdapter = BitSetAdapterJvm()
        )
    }

    @Bean
    fun issuerMessageWrapper(@Autowired issuerAgent: Agent): MessageWrapper {
        return MessageWrapper(issuerAgent.cryptoService, KeyIdServiceJvm(), JwsServiceJvm())
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