package at.asitplus.wallet.backend.config

import at.asitplus.wallet.backend.AntilogSlf4jAdapter
import at.asitplus.wallet.backend.Extensions.appendPath
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.backend.auth.SpringSecurityAuthenticationSupplier
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.data.IssuerCredentialStoreAdapter
import at.asitplus.wallet.backend.data.OidcIssuerCredentialDataProvider
import at.asitplus.wallet.backend.data.RevokedCredentialRepository
import at.asitplus.wallet.backend.service.DefaultRevocationService
import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.companyregistration.CompanyRegistrationScheme
import at.asitplus.wallet.cor.CertificateOfResidenceScheme
import at.asitplus.wallet.ehic.EhicScheme
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtScheme
import at.asitplus.wallet.healthid.HealthIdScheme
import at.asitplus.wallet.lib.agent.*
import at.asitplus.wallet.lib.data.vckJsonSerializer
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.CredentialAuthorizationServiceStrategy
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.OAuth2AuthorizationServerAdapter
import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
import at.asitplus.wallet.por.PowerOfRepresentationScheme
import at.asitplus.wallet.taxid.TaxId2025Scheme
import at.asitplus.wallet.taxid.TaxIdScheme
import io.github.aakira.napier.Napier
import jakarta.annotation.PostConstruct
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.http.converter.json.KotlinSerializationJsonHttpMessageConverter
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.util.StreamUtils
import java.io.StringReader
import java.net.URI
import java.nio.charset.Charset
import java.security.KeyStore
import java.security.PublicKey
import java.security.Security

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
        Napier.takeLogarithm()
        Napier.base(AntilogSlf4jAdapter())
        Security.addProvider(BouncyCastleProvider())
        at.asitplus.wallet.lib.Initializer.initOpenIdModule()
        at.asitplus.wallet.idaustria.Initializer.initWithVCK()
        at.asitplus.wallet.taxid.Initializer.initWithVCK()
        at.asitplus.wallet.taxid.Initializer2025.initWithVCK()
        at.asitplus.wallet.eupid.Initializer.initWithVCK()
        at.asitplus.wallet.eupidsdjwt.Initializer.initWithVCK()
        at.asitplus.wallet.mdl.Initializer.initWithVCK()
        at.asitplus.wallet.cor.Initializer.initWithVCK()
        at.asitplus.wallet.por.Initializer.initWithVCK()
        at.asitplus.wallet.healthid.Initializer.initWithVCK()
        at.asitplus.wallet.companyregistration.Initializer.initWithVCK()
        at.asitplus.wallet.ehic.Initializer.initWithVCK()
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
    fun authenticationSupplier(): AuthenticationSupplier = SpringSecurityAuthenticationSupplier()

    @Bean
    fun revocationService(
        credentialRepo: IssuedCredentialRepository,
        revokedCredentialRepo: RevokedCredentialRepository,
        applicationEventPublisher: ApplicationEventPublisher,
    ): RevocationService = DefaultRevocationService(
        credentialRepo,
        revokedCredentialRepo,
        applicationEventPublisher
    )

    @Bean
    fun issuerCredentialStoreAdapter(
        revocationService: RevocationService,
        authenticationSupplier: AuthenticationSupplier,
    ): IssuerCredentialStoreAdapter = IssuerCredentialStoreAdapter(
        revocationService,
        authenticationSupplier
    )

    @Bean
    fun issuerKeyAdapter(): KeyMaterial =
        when (configurationProperties.issuerKey.type) {
            KeyType.FILE -> loadKeyFile(configurationProperties.issuerKey.file!!, resourceLoader)
            KeyType.KEYSTORE -> loadKeyStore(configurationProperties.issuerKey.keystore!!)
            KeyType.MEMORY -> EphemeralKeyWithSelfSignedCert()
        }

    fun loadKeyStore(config: KeyStoreConfiguration) = KeyStoreMaterial(
        keyStore = KeyStore.getInstance(config.type, config.provider ?: "BC").apply {
            load(config.path.toURL().openStream(), config.password?.toCharArray() ?: charArrayOf())
        },
        keyAlias = config.alias,
        privateKeyPassword = config.aliasPassword?.toCharArray() ?: charArrayOf(),
        certAlias = config.alias
    )

    fun loadKeyFile(file: KeyFileConfiguration, resourceLoader: ResourceLoader): KeyStoreMaterial {
        val privateKeyString = loadResource(resourceLoader, file.privateKey.toString())
        val privateKeyRead = PEMParser(StringReader(privateKeyString)).readObject()
        val privateKey = JcaPEMKeyConverter().getPrivateKey(privateKeyRead as PrivateKeyInfo)
        val (jcaKey, jcaCert) = loadCertOrPubKey(file.publicKey, file.certificate, resourceLoader)
        return KeyStoreMaterial(
            KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry("alias", privateKey, charArrayOf(), jcaCert?.let { arrayOf(it) })
            },
            "alias",
            charArrayOf(),
            certAlias = jcaCert?.let { "alias" }
        )
    }

    private fun loadCertOrPubKey(
        publicKey: URI?,
        certificate: URI?,
        resourceLoader: ResourceLoader,
    ): Pair<PublicKey, java.security.cert.X509Certificate?> {
        if (publicKey == null && certificate == null) throw RuntimeException("Neither cert nor public key configured. Set one!")
        if (publicKey != null && certificate != null) throw RuntimeException("Both public key and certificate set. Set either but not both!")
        return (publicKey?.let {
            val publicKeyString = loadResource(resourceLoader, it.toString())
            val publicKeyRead = PEMParser(StringReader(publicKeyString)).readObject()
            JcaPEMKeyConverter().getPublicKey(publicKeyRead as SubjectPublicKeyInfo) to null
        } ?: certificate?.let {
            loadCertificate(resourceLoader, it).let { it.publicKey to it }
        })!!
    }

    private fun loadResource(resourceLoader: ResourceLoader, path: String) =
        StreamUtils.copyToString(resourceLoader.getResource(path).inputStream, Charset.defaultCharset())

    private fun loadCertificate(resourceLoader: ResourceLoader, src: URI) =
        JcaX509CertificateConverter().apply { setProvider("BC") }.getCertificate(
            PEMParser(StringReader(loadResource(resourceLoader, src.toString()))).readObject() as X509CertificateHolder
        )

    @Bean
    fun issuerAgent(
        issuerCredentialStore: IssuerCredentialStore,
        keyMaterial: KeyMaterial,
    ): Issuer = IssuerAgent(
        keyMaterial = keyMaterial,
        validator = Validator(),
        issuerCredentialStore = issuerCredentialStore,
        statusListBaseUrl = appendPath(configurationProperties.publicContext, "credentials", "status"),
        statusListAggregationUrl = appendPath(
            configurationProperties.publicContext,
            "credentials",
            "status",
            "current"
        ),
        revocationListLifetime = configurationProperties.revocationList.lifetimeDuration,
        cryptoAlgorithms = setOf(keyMaterial.signatureAlgorithm),
        timePeriodProvider = timePeriodProvider(),
        identifier = configurationProperties.publicContext
    )

    @Bean
    fun timePeriodProvider(): TimePeriodProvider = FixedTimePeriodProvider

    private val credentialSchemes = setOf(
        EuPidScheme,
        EuPidSdJwtScheme,
        MobileDrivingLicenceScheme,
        PowerOfRepresentationScheme,
        HealthIdScheme,
        CertificateOfResidenceScheme,
        CompanyRegistrationScheme,
        TaxIdScheme,
        TaxId2025Scheme,
        EhicScheme
    )

    @Bean
    fun issuerService(
        issuer: Issuer,
        authorizationServer: OAuth2AuthorizationServerAdapter,
        ePrescriptionLoader: EPrescriptionLoader,
    ): CredentialIssuer = CredentialIssuer(
        issuer = issuer,
        publicContext = configurationProperties.publicContext,
        credentialSchemes = credentialSchemes,
        authorizationService = authorizationServer,
        credentialProvider = OidcIssuerCredentialDataProvider(
            lifetime = configurationProperties.credentials.lifeTime,
            ePrescriptionLoader = ePrescriptionLoader
        ),
        credentialEndpointPath = "/credential",
        nonceEndpointPath = "/nonce",
    )

    @Bean
    fun authorizationServer(
        authenticationSupplier: AuthenticationSupplier,
    ): OAuth2AuthorizationServerAdapter = SimpleAuthorizationService(
        dataProvider = PreAuthnOAuth2DataProvider(authenticationSupplier),
        strategy = CredentialAuthorizationServiceStrategy(credentialSchemes),
        publicContext = configurationProperties.publicContext,
        authorizationEndpointPath = "/authorize",
        tokenEndpointPath = "/token",
        pushedAuthorizationRequestEndpointPath = "/par",
    )

    @Bean
    fun ePrescriptionLoader(restTemplateBuilder: RestTemplateBuilder): EPrescriptionLoader =
        EPrescriptionLoader(
            restTemplateBuilder,
            configurationProperties.eprescription.url,
            configurationProperties.eprescription.apiKey
        )

    @Bean
    fun messageConverter(): KotlinSerializationJsonHttpMessageConverter =
        KotlinSerializationJsonHttpMessageConverter(vckJsonSerializer)
}


