package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.ApiKeyAuthnService
import at.asitplus.wallet.backend.auth.DebugExtNonceAuthnService
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.backend.auth.NoopExtNonceAuthnService
import at.asitplus.wallet.backend.auth.SimpleApiKeyAuthnService
import at.asitplus.wallet.backend.data.DatabaseDeviceBindingStorageService
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.data.IssuerCredentialStoreAdapter
import at.asitplus.wallet.lib.DefaultKeyIdService
import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.DefaultCryptoService
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.agent.MessageWrapper
import at.asitplus.wallet.lib.data.ConstantIndex
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
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

    @Autowired
    private lateinit var restTemplateBuilder: RestTemplateBuilder

    init {
        //Napier.base(DebugAntilog())
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
    fun apiKeyAuthnService(): ApiKeyAuthnService = SimpleApiKeyAuthnService(configurationProperties.authn)

    @Bean
    fun certificateService(): CertificateService = InMemoryCertificateService()

    @Bean
    fun challengeService(): ChallengeService =
        SimpleChallengeService(lifetimeSeconds = configurationProperties.authn.challengeTimeoutSeconds)

    @Bean
    fun deviceBindingStorageService(deviceBindingRepository: DeviceBindingRepository): DeviceBindingStorageService =
        DatabaseDeviceBindingStorageService(deviceBindingRepository)

    @Bean
    fun issueCredentialAdapter(
        issueCredentialMessenger: IssueCredentialMessenger,
    ): IssueCredentialAdapter = DefaultIssueCredentialAdapter(issueCredentialMessenger)

    @Bean
    fun revocationService(
        credentialRepo: IssuedCredentialRepository,
        deviceBindingStorageService: DeviceBindingStorageService,
    ): RevocationService = DefaultRevocationService(credentialRepo, deviceBindingStorageService)

    @Bean
    fun deviceBindingAuthnService(
        deviceBindingStorageService: DeviceBindingStorageService,
        deviceBindingAuthnChallengeService: ChallengeService,
    ): DeviceBindingAuthnService =
        SimpleDeviceBindingAuthnService(deviceBindingStorageService, deviceBindingAuthnChallengeService)

    @Bean
    fun issuerCredentialStoreAdapter(
        revocationService: RevocationService,
    ): IssuerCredentialStoreAdapter = IssuerCredentialStoreAdapter(revocationService)

    @Bean
    fun issuerCredentialDataProvider(): IssuerCredentialDataProvider =
        when (configurationProperties.attributeSource.type) {
            AttributeSourceType.RANDOM -> {
                val locationPattern = "${configurationProperties.attributeSource.random!!.photoLocation}/*.jpg"
                val mapOfPhotos = resourcePatternResolver.getResources(locationPattern)
                    .filter { it.exists() }
                    .filter { it.filename != null }
                    .map { it.filename!! to it.inputStream }
                    .map { it.first to it.second.readAllBytes() }
                RandomCredentialDataProvider(
                    configurationProperties.credentialLifetime.toMinutes().minutes,
                    mapOfPhotos.toMap()
                )
            }
            AttributeSourceType.ECO -> {
                val restTemplate = ClientTlsConfigurationService(
                    configurationProperties.attributeSource.eco!!,
                    restTemplateBuilder
                ).restTemplate
                EcoCredentialDataProvider(
                    configurationProperties.credentialLifetime.toMinutes().minutes,
                    configurationProperties.attributeSource.eco!!.url.toString(),
                    restTemplate,
                )
            }
            AttributeSourceType.EIDAS -> {
                EidasCredentialDataProvider(
                    configurationProperties.credentialLifetime.toMinutes().minutes
                )
            }
        }

    @Bean
    fun issuerCryptoService() = when (configurationProperties.issuerKey.type) {
        KeyType.FILE -> FileCryptoService(
            KeyFileAdapter(configurationProperties.issuerKey.file!!, resourceLoader),
            DefaultKeyIdService()
        )
        KeyType.KEYSTORE -> FileCryptoService(
            KeyStoreAdapter(configurationProperties.issuerKey.keystore!!),
            DefaultKeyIdService(),
        )
        KeyType.MEMORY -> DefaultCryptoService()
    }

    @Bean
    fun issuerAgent(
        issuerCredentialStore: IssuerCredentialStore,
        issuerCredentialDataProvider: IssuerCredentialDataProvider,
        issuerCryptoService: CryptoService
    ): Agent = Agent(
        cryptoService = issuerCryptoService,
        issuerCredentialStore = issuerCredentialStore,
        dataProvider = issuerCredentialDataProvider,
        revocationListUrl = "${configurationProperties.publicContext}/credentials/status/1",
    )

    @Bean
    fun issuerMessageWrapper(issuerAgent: Agent): MessageWrapper = MessageWrapper(issuerAgent.cryptoService)

    @Profile("pupilid")
    @Bean
    fun issueCredentialMessengerPupilId(
        issuerAgent: Agent,
        issuerMessageWrapper: MessageWrapper
    ): IssueCredentialMessenger = IssueCredentialMessenger(
        issuerAgent,
        issuerMessageWrapper,
        "${configurationProperties.publicContext}/pupilid/issue",
        true,
        credentialScheme = ConstantIndex.PupilId,
    )

    @Profile("eidasid")
    @Bean
    fun issueCredentialMessengerEidasId(
        issuerAgent: Agent,
        issuerMessageWrapper: MessageWrapper
    ): IssueCredentialMessenger = IssueCredentialMessenger(
        issuerAgent,
        issuerMessageWrapper,
        "${configurationProperties.publicContext}/eidasid/issue",
        true,
        credentialScheme = ConstantIndex.Generic,
    )

}