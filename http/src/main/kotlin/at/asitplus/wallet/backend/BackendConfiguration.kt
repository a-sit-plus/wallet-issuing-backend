package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.ApiKeyAuthnService
import at.asitplus.wallet.backend.auth.EcoExtNonceAuthnService
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.backend.auth.InternalExtNonceAuthnService
import at.asitplus.wallet.backend.auth.SimpleApiKeyAuthnService
import at.asitplus.wallet.backend.data.DatabaseDeviceBindingStorageService
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.data.IssuerCredentialStoreAdapter
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.DefaultCryptoService
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.IssuerAgent
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.agent.MessageWrapper
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.jws.DefaultJwsService
import io.github.aakira.napier.Napier
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
        Napier.base(AntilogSlf4jAdapter())
    }

    @Bean
    fun extNonceAuthnService(): ExtNonceAuthnService {
        return when (configurationProperties.authn.deviceBinding.type) {
            DeviceBindingNonceType.INTERNAL -> InternalExtNonceAuthnService(
                SimpleChallengeService(lifetimeSeconds = configurationProperties.authn.challengeTimeoutSeconds)
            )
            DeviceBindingNonceType.ECO -> {
                val restTemplate = ClientTlsConfigurationService(
                    configurationProperties.authn.deviceBinding.eco,
                    restTemplateBuilder
                ).restTemplate
                EcoExtNonceAuthnService(
                    configurationProperties.authn.deviceBinding.eco.url!!,
                    restTemplate
                )
            }
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
    ): RevocationService = DefaultRevocationService(
        credentialRepo,
        deviceBindingStorageService,
        configurationProperties.credentials.oneCredentialPerDeviceBinding
    )

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
    fun dataProvider(deviceBindingStorageService: DeviceBindingStorageService): CredentialDataProvider =
        when (configurationProperties.attributeSource.type) {
            AttributeSourceType.RANDOM -> {
                val locationPattern = "${configurationProperties.attributeSource.random!!.photoLocation}/*.jpg"
                val mapOfPhotos = resourcePatternResolver.getResources(locationPattern)
                    .filter { it.exists() }
                    .filter { it.filename != null }
                    .map { it.filename!! to it.inputStream }
                    .map { it.first to it.second.readAllBytes() }
                RandomCredentialDataProvider(
                    mapOfPhotos.toMap(),
                    deviceBindingStorageService,
                )
            }
            AttributeSourceType.ECO -> {
                val restTemplate = ClientTlsConfigurationService(
                    configurationProperties.attributeSource.eco!!,
                    restTemplateBuilder
                ).restTemplate
                // TODO Remove once ECO provides pictures
                val listOfPhotos = if (configurationProperties.attributeSource.random != null) {
                    val locationPattern = "${configurationProperties.attributeSource.random!!.photoLocation}/*.jpg"
                    resourcePatternResolver.getResources(locationPattern)
                        .filter { it.exists() }
                        .filter { it.filename != null }
                        .map { it.inputStream }
                        .map { it.readAllBytes() }
                        .toList()
                } else {
                    listOf(byteArrayOf())
                }
                EcoCredentialDataProvider(
                    configurationProperties.attributeSource.eco!!.url.toString(),
                    restTemplate,
                    listOfPhotos,
                )
            }
            AttributeSourceType.EIDAS -> {
                EidasCredentialDataProvider(
                )
            }
        }

    @Bean
    fun issuerCredentialDataProvider(
        credentialDataProvider: CredentialDataProvider,
        deviceBindingStorageService: DeviceBindingStorageService
    ): IssuerCredentialDataProvider =
        IssuerCredentialDataProviderAdapter(
            lifetime = configurationProperties.credentials.lifetime.toMinutes().minutes,
            credentialDataProvider = credentialDataProvider,
            deviceBindingStorageService = deviceBindingStorageService
        )


    @Bean
    fun issuerCryptoService() = when (configurationProperties.issuerKey.type) {
        KeyType.FILE -> FileCryptoService(
            KeyFileAdapter(configurationProperties.issuerKey.file!!, resourceLoader),
        )
        KeyType.KEYSTORE -> FileCryptoService(
            KeyStoreAdapter(configurationProperties.issuerKey.keystore!!),
        )
        KeyType.MEMORY -> DefaultCryptoService()
    }

    @Bean
    fun issuerAgent(
        issuerCredentialStore: IssuerCredentialStore,
        issuerCredentialDataProvider: IssuerCredentialDataProvider,
        issuerCryptoService: CryptoService
    ): IssuerAgent = IssuerAgent(
        keyId = issuerCryptoService.keyId,
        jwsService = DefaultJwsService(issuerCryptoService),
        issuerCredentialStore = issuerCredentialStore,
        dataProvider = issuerCredentialDataProvider,
        revocationListUrl = "${configurationProperties.publicContext}/credentials/status/1",
    )

    @Bean
    fun issuerMessageWrapper(issuerCryptoService: CryptoService): MessageWrapper = MessageWrapper(
        cryptoService = issuerCryptoService,
        jwsService = DefaultJwsService(issuerCryptoService)
    )

    @Profile("pupilid")
    @Bean
    fun issueCredentialMessengerPupilId(
        issuerAgent: IssuerAgent,
        issuerCryptoService: CryptoService,
        issuerMessageWrapper: MessageWrapper
    ): IssueCredentialMessenger = IssueCredentialMessenger.newIssuerInstance(
        issuer = issuerAgent,
        messageWrapper = issuerMessageWrapper,
        keyId = issuerCryptoService.keyId,
        serviceEndpoint = "${configurationProperties.publicContext}/pupilid/issue",
        credentialScheme = ConstantIndex.PupilId,
    )

    @Profile("eidasid")
    @Bean
    fun issueCredentialMessengerEidasId(
        issuerAgent: IssuerAgent,
        issuerCryptoService: CryptoService,
        issuerMessageWrapper: MessageWrapper
    ): IssueCredentialMessenger = IssueCredentialMessenger.newIssuerInstance(
        issuer = issuerAgent,
        messageWrapper = issuerMessageWrapper,
        keyId = issuerCryptoService.keyId,
        serviceEndpoint = "${configurationProperties.publicContext}/eidasid/issue",
        credentialScheme = ConstantIndex.Generic,
    )

}

