package at.asitplus.wallet.backend.config

import at.asitplus.attestation.AttestationService
import at.asitplus.attestation.DefaultAttestationService
import at.asitplus.attestation.NoopAttestationService
import at.asitplus.attestation.android.AndroidAttestationConfiguration
import at.asitplus.attestation.android.PatchLevel
import at.asitplus.wallet.backend.AntilogSlf4jAdapter
import at.asitplus.wallet.backend.Extensions.appendPath
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.backend.auth.SpringSecurityAuthenticationSupplier
import at.asitplus.wallet.backend.data.CredentialDataProvider
import at.asitplus.wallet.backend.data.EidasCredentialDataProvider
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.data.IssuerCredentialDataProviderAdapter
import at.asitplus.wallet.backend.data.IssuerCredentialStoreAdapter
import at.asitplus.wallet.backend.data.RandomCredentialDataProvider
import at.asitplus.wallet.backend.data.RevokedCredentialRepository
import at.asitplus.wallet.backend.pki.HsmFacadeAdapter
import at.asitplus.wallet.backend.pki.KeyFileAdapter
import at.asitplus.wallet.backend.pki.KeyStoreAdapter
import at.asitplus.wallet.backend.pki.RandomKeyAdapter
import at.asitplus.wallet.backend.pki.SecurityProviderBean
import at.asitplus.wallet.backend.service.DefaultCryptoServiceAdapter
import at.asitplus.wallet.backend.service.DefaultRevocationService
import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.DefaultVerifierCryptoService
import at.asitplus.wallet.lib.agent.FixedTimePeriodProvider
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.agent.IssuerAgent
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.agent.TimePeriodProvider
import at.asitplus.wallet.lib.agent.Validator
import at.asitplus.wallet.lib.aries.MessageWrapper
import at.asitplus.wallet.lib.cbor.DefaultCoseService
import at.asitplus.wallet.lib.jws.DefaultJwsService
import at.asitplus.wallet.lib.oidvci.IssuerService
import at.asitplus.wallet.pupilid.SchoolyearBasedTimePeriodProvider
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArrayOrNull
import jakarta.annotation.PostConstruct
import kotlinx.datetime.Clock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.scheduling.annotation.EnableScheduling
import kotlin.time.Duration.Companion.seconds

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

    @Autowired
    private lateinit var resourcePatternResolver: ResourcePatternResolver

    init {
        at.asitplus.wallet.idaustria.Initializer.initWithVcLib()
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
        SecurityProviderBean(configurationProperties, resourceLoader)

    @Bean
    fun attestationService(
    ): AttestationService = if (configurationProperties.authn.deviceBinding.attestation.noop != true)
        DefaultAttestationService(
            androidAttestationConfiguration(),
            (configurationProperties.authn.deviceBinding.attestation.ios
                ?: throw RuntimeException("no iOS Attestation configured")).toIosAttestationConfiguration(),
            Clock.System,
            configurationProperties.authn.deviceBinding.attestation.verificationOffSetDuration
        )
    else {
        if (configurationProperties.authn.deviceBinding.attestation.ios != null || configurationProperties.authn.deviceBinding.attestation.android != null)
            throw RuntimeException("As precautionary measure, attestation can only be disabled if neither Android nor iOS attestation are configured!")
        Napier.w(
            """



.o. .o. .o. oooooo   oooooo     oooo       .o.       ooooooooo.   ooooo      ooo ooooo ooooo      ooo   .oooooo.    .o. .o. .o. 
888 888 888  `888.    `888.     .8'       .888.      `888   `Y88. `888b.     `8' `888' `888b.     `8'  d8P'  `Y8b   888 888 888 
888 888 888   `888.   .8888.   .8'       .8"888.      888   .d88'  8 `88b.    8   888   8 `88b.    8  888           888 888 888 
Y8P Y8P Y8P    `888  .8'`888. .8'       .8' `888.     888ooo88P'   8   `88b.  8   888   8   `88b.  8  888           Y8P Y8P Y8P 
`8' `8' `8'     `888.8'  `888.8'       .88ooo8888.    888`88b.     8     `88b.8   888   8     `88b.8  888     ooooo `8' `8' `8' 
.o. .o. .o.      `888'    `888'       .8'     `888.   888  `88b.   8       `888   888   8       `888  `88.    .88'  .o. .o. .o. 
Y8P Y8P Y8P       `8'      `8'       o88o     o8888o o888o  o888o o8o        `8  o888o o8o        `8   `Y8bood8P'   Y8P Y8P Y8P 



                .o.           .       .                          .                 .    o8o                                     
               .888.        .o8     .o8                        .o8               .o8    `"'                                     
              .8"888.     .o888oo .o888oo  .ooooo.   .oooo.o .o888oo  .oooo.   .o888oo oooo   .ooooo.  ooo. .oo.                
             .8' `888.      888     888   d88' `88b d88(  "8   888   `P  )88b    888   `888  d88' `88b `888P"Y88b               
            .88ooo8888.     888     888   888ooo888 `"Y88b.    888    .oP"888    888    888  888   888  888   888               
           .8'     `888.    888 .   888 . 888    .o o.  )88b   888 . d8(  888    888 .  888  888   888  888   888               
          o88o     o8888o   "888"   "888" `Y8bod8P' 8""888P'   "888" `Y888""8o   "888" o888o `Y8bod8P' o888o o888o              



                          .o8   o8o                      .o8       oooo                  .o8  .o.                               
                         "888   `"'                     "888       `888                 "888  888                               
                     .oooo888  oooo   .oooo.o  .oooo.    888oooo.   888   .ooooo.   .oooo888  888                               
                    d88' `888  `888  d88(  "8 `P  )88b   d88' `88b  888  d88' `88b d88' `888  Y8P                               
                    888   888   888  `"Y88b.   .oP"888   888   888  888  888ooo888 888   888  `8'                               
                    888   888   888  o.  )88b d8(  888   888   888  888  888    .o 888   888  .o.                               
                    `Y8bod88P" o888o 8""888P' `Y888""8o  `Y8bod8P' o888o `Y8bod8P' `Y8bod88P" Y8P



"""
        )
        NoopAttestationService
    }


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
    fun dataProvider(
        authenticationSupplier: AuthenticationSupplier,
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
                RandomCredentialDataProvider(mapOfPhotos.toMap())
            }

            AttributeSourceType.EIDAS -> {
                EidasCredentialDataProvider(600.seconds, authenticationSupplier)
            }
        }

    @Bean
    fun issuerCredentialDataProvider(
        credentialDataProvider: CredentialDataProvider
    ): IssuerCredentialDataProvider = IssuerCredentialDataProviderAdapter(
        lifetime = configurationProperties.credentials.lifeTime,
        credentialDataProvider = credentialDataProvider,
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
        }
    )

    @Bean
    fun issuerAgent(
        issuerCredentialStore: IssuerCredentialStore,
        issuerCredentialDataProvider: IssuerCredentialDataProvider,
        issuerCryptoService: CryptoService
    ): Issuer = IssuerAgent(
        identifier = issuerCryptoService.identifier,
        jwsService = DefaultJwsService(issuerCryptoService),
        issuerCredentialStore = issuerCredentialStore,
        dataProvider = issuerCredentialDataProvider,
        revocationListBaseUrl = appendPath(
            configurationProperties.publicContext,
            "credentials",
            "status"
        ),
        revocationListLifetime = configurationProperties.revocationList.lifetimeDuration,
        timePeriodProvider = timePeriodProvider(),
        validator = Validator.newDefaultInstance(DefaultVerifierCryptoService()),
        coseService = DefaultCoseService(issuerCryptoService)
    )

    @Bean
    fun timePeriodProvider(): TimePeriodProvider = FixedTimePeriodProvider

    @Bean
    fun issuerMessageWrapper(
        issuerCryptoService: CryptoService
    ): MessageWrapper = MessageWrapper(
        cryptoService = issuerCryptoService,
        jwsService = DefaultJwsService(issuerCryptoService)
    )

    @Bean
    fun issuerService(
        issuer: Issuer
    ): IssuerService = IssuerService(
        issuer = issuer,
        publicContext = configurationProperties.publicContext,
        credentialSchemes = listOf(
            at.asitplus.wallet.idaustria.ConstantIndex.IdAustriaCredential,
            at.asitplus.wallet.lib.data.ConstantIndex.MobileDrivingLicence2023
        ),
        authorizationServer = "https://eid2.oesterreich.gv.at/",
    )

    private fun androidAttestationConfiguration(): AndroidAttestationConfiguration {
        val aCfg = configurationProperties.authn.deviceBinding.attestation.android
            ?: throw IllegalArgumentException("No Android attestation configured")
        return AndroidAttestationConfiguration(
            packageName = aCfg.packageName,
            signatureDigests = aCfg.signatureDigests.map {
                it.decodeToByteArrayOrNull(Base16())
                    ?: throw IllegalArgumentException("Could not hex decode Android attestation signature digest $it")
            },
            appVersion = aCfg.applicationVersion,
            androidVersion = aCfg.androidVersion,
            patchLevel = aCfg.patchLevel?.let { PatchLevel(it.year, it.month) },
            requireStrongBox = aCfg.requireStrongBox,
            bootloaderUnlockAllowed = false,
            requireRollbackResistance = aCfg.requireRollbackResistance,
            ignoreLeafValidity = aCfg.ignoreLeafValidity
        )
    }


}


