package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.Extensions.appendPath
import at.asitplus.wallet.backend.auth.*
import at.asitplus.wallet.backend.data.DatabaseDeviceBindingStorageService
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.data.IssuerCredentialStoreAdapter
import at.asitplus.wallet.lib.agent.*
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.jws.DefaultJwsService
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.scheduling.annotation.EnableScheduling
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@Configuration
@EnableConfigurationProperties(value = [BackendConfigurationProperties::class])
@EnableScheduling
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
    fun securityProviderBean(): SecurityProviderBean =
        SecurityProviderBean(configurationProperties, resourceLoader)

    @Bean
    fun extNonceAuthnService(): ExtNonceAuthnService =
        when (configurationProperties.authn.deviceBinding.type) {
            DeviceBindingNonceType.INTERNAL -> InternalExtNonceAuthnService(
                SimpleChallengeService(
                    lifetimeSeconds = configurationProperties.authn.challengeTimeoutSeconds,
                    clock = clock()
                )
            )
            DeviceBindingNonceType.ECO -> {
                val restTemplate = RestTemplateConfigurationService(
                    configurationProperties.authn.deviceBinding.eco,
                    restTemplateBuilder
                ).restTemplate
                EcoExtNonceAuthnService(
                    configurationProperties.authn.deviceBinding.eco.url!!,
                    restTemplate
                )
            }
        }

    @Bean
    fun bindingService(
        challengeService: ChallengeService,
        pkiService: PkiService,
        attestationService: AttestationService,
        deviceBindingStorageService: DeviceBindingStorageService
    ): BindingService =
        DefaultBindingService(
            challengeService,
            pkiService,
            attestationService,
            deviceBindingStorageService
        )

    @Bean
    fun apiKeyAuthnService(): ApiKeyAuthnService =
        SimpleApiKeyAuthnService(configurationProperties.authn)

    @Bean
    fun pkiService(
        securityProviderBean: SecurityProviderBean
    ): PkiService = when (configurationProperties.pki.type) {
        PkiType.INTERNAL -> {
            val keyAdapter = when (configurationProperties.pki.internal.key.type) {
                KeyType.FILE -> KeyFileAdapter(
                    configurationProperties.pki.internal.key.file!!,
                    resourceLoader,
                    securityProviderBean
                )
                KeyType.KEYSTORE -> KeyStoreAdapter(
                    configurationProperties.pki.internal.key.keystore!!,
                    securityProviderBean
                )
                KeyType.HSMFACADE -> HsmFacadeAdapter(
                    configurationProperties.pki.internal.key.hsmfacade!!,
                    securityProviderBean
                )
                KeyType.MEMORY -> RandomKeyAdapter()
                KeyType.REMOTE -> RemoteKeyAdapter(
                    configurationProperties.pki.internal.key.remote!!,
                    securityProviderBean
                )
            }
            InMemoryPkiService(
                configurationProperties.pki.certValidityDays.days,
                configurationProperties.pki.internal.issuerName,
                DefaultCryptoServiceAdapter(keyAdapter),
                clock()
            )
        }
        PkiType.AERA -> {
            val restTemplate = RestTemplateConfigurationService(
                configurationProperties.pki.aera,
                restTemplateBuilder
            ).restTemplate
            AeraPkiService(
                configurationProperties.pki.certValidityDays.days,
                configurationProperties.pki.aera.url!!.toString(),
                restTemplate,
                clock()
            )
        }
    }

    @Bean
    fun attestationService(
        issuerCryptoService: CryptoServiceAdapter
    ): AttestationService =
        DefaultAttestationService(issuerCryptoService)

    @Bean
    fun challengeService(): ChallengeService =
        SimpleChallengeService(
            lifetimeSeconds = configurationProperties.authn.challengeTimeoutSeconds,
            clock = clock()
        )

    @Bean
    fun authenticationSupplier(): AuthenticationSupplier = SpringSecurityAuthenticationSupplier()

    @Bean
    fun deviceBindingStorageService(
        deviceBindingRepository: DeviceBindingRepository,
        authenticationSupplier: AuthenticationSupplier,
    ): DeviceBindingStorageService =
        DatabaseDeviceBindingStorageService(
            deviceBindingRepository,
            authenticationSupplier,
            clock = clock()
        )

    @Bean
    fun issueCredentialAdapter(
        issueCredentialMessenger: IssueCredentialMessenger
    ): IssueCredentialAdapter =
        DefaultIssueCredentialAdapter(issueCredentialMessenger)

    @Bean
    fun revocationService(
        credentialRepo: IssuedCredentialRepository,
        deviceBindingStorageService: DeviceBindingStorageService,
        pkiService: PkiService,
    ): RevocationService = DefaultRevocationService(
        credentialRepo,
        deviceBindingStorageService,
        configurationProperties.credentials.oneCredentialPerDeviceBinding,
        pkiService,
        clock()
    )

    @Bean
    fun clock(): Clock = when (configurationProperties.timeSource) {
        TimeSource.SYSTEM -> Clock.System
        TimeSource.TEST -> TestTimeSource.clock
    }

    @Bean
    fun deviceBindingAuthnService(
        deviceBindingStorageService: DeviceBindingStorageService,
        deviceBindingAuthnChallengeService: ChallengeService,
    ): DeviceBindingAuthnService =
        SimpleDeviceBindingAuthnService(
            deviceBindingStorageService,
            deviceBindingAuthnChallengeService
        )

    @Bean
    fun issuerCredentialStoreAdapter(
        revocationService: RevocationService
    ): IssuerCredentialStoreAdapter = IssuerCredentialStoreAdapter(revocationService)

    @Bean
    fun dataProvider(
        deviceBindingStorageService: DeviceBindingStorageService
    ): CredentialDataProvider =
        when (configurationProperties.attributeSource.type) {
            AttributeSourceType.RANDOM -> {
                val locationPattern =
                    "${configurationProperties.attributeSource.random!!.photoLocation}/*.jpg"
                val mapOfPhotos = resourcePatternResolver.getResources(locationPattern)
                    .filter { it.exists() }
                    .filter { it.filename != null }
                    .map { it.filename!! to it.inputStream }
                    .map { it.first to it.second.readAllBytes() }
                RandomCredentialDataProvider(
                    mapOfPhotos.toMap(),
                )
            }
            AttributeSourceType.ECO -> {
                val restTemplate = RestTemplateConfigurationService(
                    configurationProperties.attributeSource.eco!!,
                    restTemplateBuilder
                ).restTemplate
                EcoCredentialDataProvider(
                    configurationProperties.attributeSource.eco!!.url.toString(),
                    restTemplate,
                )
            }
            AttributeSourceType.EIDAS -> {
                EidasCredentialDataProvider(
                    600.seconds,
                    clock = clock()
                )
            }
        }

    @Bean
    fun issuerCredentialDataProvider(
        credentialDataProvider: CredentialDataProvider,
        deviceBindingStorageService: DeviceBindingStorageService
    ): IssuerCredentialDataProvider = IssuerCredentialDataProviderAdapter(
        lifetime = configurationProperties.credentials.lifeTime,
        credentialDataProvider = credentialDataProvider,
        deviceBindingStorageService = deviceBindingStorageService,
        gracePeriod = configurationProperties.credentials.gracePeriodDuration,
        clock = clock()
    )

    @Bean
    fun issuerCryptoService(
        securityProviderBean: SecurityProviderBean
    ) = DefaultCryptoServiceAdapter(
        when (configurationProperties.issuerKey.type) {
            KeyType.FILE -> KeyFileAdapter(
                configurationProperties.issuerKey.file!!,
                resourceLoader,
                securityProviderBean
            )
            KeyType.KEYSTORE -> KeyStoreAdapter(
                configurationProperties.issuerKey.keystore!!,
                securityProviderBean
            )
            KeyType.HSMFACADE -> HsmFacadeAdapter(
                configurationProperties.issuerKey.hsmfacade!!,
                securityProviderBean
            )
            KeyType.MEMORY -> RandomKeyAdapter()
            KeyType.REMOTE -> RemoteKeyAdapter(
                configurationProperties.issuerKey.remote!!,
                securityProviderBean
            )
        }
    )

    @Bean
    fun issuerAgent(
        issuerCredentialStore: IssuerCredentialStore,
        issuerCredentialDataProvider: IssuerCredentialDataProvider,
        issuerCryptoService: CryptoService
    ): Issuer = IssuerAgent(
        keyId = issuerCryptoService.keyId,
        jwsService = DefaultJwsService(issuerCryptoService),
        issuerCredentialStore = issuerCredentialStore,
        dataProvider = issuerCredentialDataProvider,
        //TODO
        revocationListBaseUrl = appendPath(
            configurationProperties.publicContext,
            "credentials",
            "status"
        ),
        schoolYearStart = configurationProperties.schooYearStart,
        clock = clock()
    )

    @Bean
    fun issuerMessageWrapper(
        issuerCryptoService: CryptoService
    ): MessageWrapper = MessageWrapper(
        cryptoService = issuerCryptoService,
        jwsService = DefaultJwsService(issuerCryptoService)
    )

    @Profile("pupilid")
    @Bean
    fun issueCredentialMessengerPupilId(
        issuer: Issuer,
        issuerCryptoService: CryptoService,
        issuerMessageWrapper: MessageWrapper
    ): IssueCredentialMessenger = IssueCredentialMessenger.newIssuerInstance(
        issuer = issuer,
        messageWrapper = issuerMessageWrapper,
        keyId = issuerCryptoService.keyId,
        serviceEndpoint = appendPath(configurationProperties.publicContext, "pupilid", "issue"),
        credentialScheme = ConstantIndex.PupilId,
    )

    @Profile("eidasid")
    @Bean
    fun issueCredentialMessengerEidasId(
        issuer: Issuer,
        issuerCryptoService: CryptoService,
        issuerMessageWrapper: MessageWrapper
    ): IssueCredentialMessenger = IssueCredentialMessenger.newIssuerInstance(
        issuer = issuer,
        messageWrapper = issuerMessageWrapper,
        keyId = issuerCryptoService.keyId,
        serviceEndpoint = appendPath(configurationProperties.publicContext, "eidasid", "issue"),
        credentialScheme = ConstantIndex.Generic,
    )

}

