package at.asitplus.wallet.backend.config

import at.asitplus.crypto.datatypes.X509SignatureAlgorithm
import at.asitplus.wallet.backend.AntilogSlf4jAdapter
import at.asitplus.wallet.backend.Extensions.appendPath
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.backend.auth.SpringSecurityAuthenticationSupplier
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.data.IssuerCredentialDataProviderAdapter
import at.asitplus.wallet.backend.data.IssuerCredentialStoreAdapter
import at.asitplus.wallet.backend.data.RevokedCredentialRepository
import at.asitplus.wallet.backend.pki.KeyFileAdapter
import at.asitplus.wallet.backend.pki.KeyStoreAdapter
import at.asitplus.wallet.backend.pki.RandomKeyAdapter
import at.asitplus.wallet.backend.pki.SecurityProviderBean
import at.asitplus.wallet.backend.service.DefaultCryptoServiceAdapter
import at.asitplus.wallet.backend.service.DefaultRevocationService
import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.DefaultVerifierCryptoService
import at.asitplus.wallet.lib.agent.FixedTimePeriodProvider
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.agent.IssuerAgent
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.agent.TimePeriodProvider
import at.asitplus.wallet.lib.agent.Validator
import at.asitplus.wallet.lib.cbor.DefaultCoseService
import at.asitplus.wallet.lib.jws.DefaultJwsService
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.OAuth2AuthorizationServer
import at.asitplus.wallet.lib.oidvci.SimpleAuthorizationService
import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
import io.github.aakira.napier.Napier
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableConfigurationProperties(value = [BackendConfigurationProperties::class])
@EnableScheduling
class BackendConfiguration {

    companion object {
        //https://gist.github.com/bnorm/71c7973b4b3f928e855a183a3e56c791
        fun String.toIndentString(): String = buildString(length) {
            var indent = 0

            fun line() {
                appendLine()
                repeat(2 * indent) { append(' ') }
            }

            this@toIndentString.filter { it != ' ' }.forEach { char ->
                when (char) {
                    ')', ']', '}' -> {
                        indent--
                        line()
                        append(char)
                    }

                    '=' -> append(" = ")
                    '(', '[', '{', ',' -> {
                        append(char)
                        if (char != ',') indent++
                        line()
                    }

                    else -> append(char)
                }
            }
        }
    }

    @Autowired
    private lateinit var configurationProperties: BackendConfigurationProperties

    @Autowired
    private lateinit var resourceLoader: ResourceLoader

    init {
        at.asitplus.wallet.idaustria.Initializer.initWithVcLib()
        at.asitplus.wallet.eupid.Initializer.initWithVcLib()
        at.asitplus.wallet.mdl.Initializer.initWithVcLib()
        at.asitplus.wallet.cor.Initializer.initWithVcLib()
        at.asitplus.wallet.por.Initializer.initWithVcLib()
        Napier.takeLogarithm()
        Napier.base(AntilogSlf4jAdapter())
    }

    @PostConstruct
    private fun logConfig() {
        Napier.i("******** Current Configuration ********")

        Napier.i(
            "\n" + configurationProperties.toString()
                .replace(Regex("password=.*?,"), "password=***,").toIndentString()
        )
        Napier.i("***************************************")
    }

    @Bean
    fun securityProviderBean(): SecurityProviderBean =
        SecurityProviderBean()

    @Bean
    fun authenticationSupplier(): AuthenticationSupplier = SpringSecurityAuthenticationSupplier()

    @Bean
    fun revocationService(
        credentialRepo: IssuedCredentialRepository,
        revokedCredentialRepo: RevokedCredentialRepository,
        applicationEventPublisher: ApplicationEventPublisher,
    ): RevocationService = DefaultRevocationService(
        credentialRepo,
        revokedCredentialRepo,
        applicationEventPublisher,
    )

    @Bean
    fun issuerCredentialStoreAdapter(
        revocationService: RevocationService
    ): IssuerCredentialStoreAdapter = IssuerCredentialStoreAdapter(revocationService)

    @Bean
    fun issuerCredentialDataProvider(
        authenticationSupplier: AuthenticationSupplier,
    ): IssuerCredentialDataProvider = IssuerCredentialDataProviderAdapter(
        lifetime = configurationProperties.credentials.lifeTime,
        authenticationSupplier = authenticationSupplier,
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

            KeyType.MEMORY -> RandomKeyAdapter()
        }
    )

    @Bean
    fun issuerAgent(
        issuerCredentialStore: IssuerCredentialStore,
        issuerCredentialDataProvider: IssuerCredentialDataProvider,
        issuerCryptoService: CryptoService
    ): Issuer = IssuerAgent(
        identifier = issuerCryptoService.publicKey.didEncoded,
        jwsService = DefaultJwsService(issuerCryptoService),
        issuerCredentialStore = issuerCredentialStore,
        dataProvider = issuerCredentialDataProvider,
        revocationListBaseUrl = appendPath(
            configurationProperties.publicContext, "credentials", "status"
        ),
        revocationListLifetime = configurationProperties.revocationList.lifetimeDuration,
        timePeriodProvider = timePeriodProvider(),
        validator = Validator.newDefaultInstance(DefaultVerifierCryptoService()),
        coseService = DefaultCoseService(issuerCryptoService),
        cryptoAlgorithms = setOf(X509SignatureAlgorithm.ES256)
    )

    @Bean
    fun timePeriodProvider(): TimePeriodProvider = FixedTimePeriodProvider

    @Bean
    fun issuerService(
        issuer: Issuer,
        authorizationServer: OAuth2AuthorizationServer,
    ): CredentialIssuer = CredentialIssuer(
        issuer = issuer,
        publicContext = configurationProperties.publicContext,
        credentialSchemes = setOf(IdAustriaScheme, EuPidScheme, MobileDrivingLicenceScheme),
        authorizationService = authorizationServer,
        buildIssuerCredentialDataProviderOverride = { oidcUserInfo ->
            OidcIssuerCredentialDataProvider(
                userInfo = oidcUserInfo,
                lifetime = configurationProperties.credentials.lifeTime,
            )
        }
    )

    @Bean
    fun authorizationServer(
        authenticationSupplier: AuthenticationSupplier,
    ): SimpleAuthorizationService =
        SimpleAuthorizationService(
            dataProvider = PreAuthnOAuth2DataProvider(authenticationSupplier),
            credentialSchemes = setOf(IdAustriaScheme, EuPidScheme, MobileDrivingLicenceScheme),
            publicContext = configurationProperties.publicContext,
            authorizationEndpointPath = "/authorize",
            tokenEndpointPath = "/token",
        )


}


