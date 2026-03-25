package at.asitplus.wallet.backend.config

import at.asitplus.wallet.ageverification.AgeVerificationScheme
import at.asitplus.wallet.backend.AntilogSlf4jAdapter
import at.asitplus.wallet.backend.Extensions.appendPath
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.data.IdentityColumnResynchronizer
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.data.IssuerCredentialStoreAdapter
import at.asitplus.wallet.backend.data.PreparedCredentialRepository
import at.asitplus.wallet.backend.data.RevokedCredentialRepository
import at.asitplus.wallet.backend.service.DefaultRevocationService
import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.cor.CertificateOfResidenceScheme
import at.asitplus.wallet.ehic.EhicScheme
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtScheme
import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.agent.FixedTimePeriodProvider
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.agent.IssuerAgent
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.KeyStoreMaterial
import at.asitplus.wallet.lib.agent.StatusListAgent
import at.asitplus.wallet.lib.agent.StatusListIssuer
import at.asitplus.wallet.lib.agent.TimePeriodProvider
import at.asitplus.wallet.lib.data.rfc3986.UniformResourceIdentifier
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.agents.ReferencedTokenStore
import at.asitplus.wallet.lib.data.vckJsonSerializer
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oauth2.TokenService
import at.asitplus.wallet.lib.oidvci.CredentialAuthorizationServiceStrategy
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.OAuth2AuthorizationServerAdapter
import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
import at.asitplus.wallet.por.PowerOfRepresentationScheme
import at.asitplus.wallet.taxid.TaxIdScheme
import io.github.aakira.napier.Napier
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
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

    @Autowired
    private lateinit var configuration: BackendConfigurationProperties

    @Autowired
    private lateinit var resourceLoader: ResourceLoader

    init {
        Napier.takeLogarithm()
        Napier.base(AntilogSlf4jAdapter())
        Security.addProvider(BouncyCastleProvider())
        at.asitplus.wallet.taxid.Initializer.initWithVCK()
        at.asitplus.wallet.eupid.Initializer.initWithVCK()
        at.asitplus.wallet.eupidsdjwt.Initializer.initWithVCK()
        at.asitplus.wallet.mdl.Initializer.initWithVCK()
        at.asitplus.wallet.cor.Initializer.initWithVCK()
        at.asitplus.wallet.por.Initializer.initWithVCK()
        at.asitplus.wallet.ehic.Initializer.initWithVCK()
        at.asitplus.wallet.ageverification.Initializer.initWithVCK()
    }

    @Bean
    fun revocationService(
        preparedCredentialRepo: PreparedCredentialRepository,
        credentialRepo: IssuedCredentialRepository,
        revokedCredentialRepo: RevokedCredentialRepository,
        applicationEventPublisher: ApplicationEventPublisher,
        identityColumnResynchronizer: IdentityColumnResynchronizer,
    ): RevocationService = DefaultRevocationService(
        preparedCredentialRepo = preparedCredentialRepo,
        issuedCredentialRepo = credentialRepo,
        revokedCredentialRepo = revokedCredentialRepo,
        applicationEventPublisher = applicationEventPublisher,
        identityColumnResynchronizer = identityColumnResynchronizer,
    )

    @Bean
    fun issuerCredentialStoreAdapter(
        revocationService: RevocationService,
    ): IssuerCredentialStoreAdapter = IssuerCredentialStoreAdapter(
        revocationService = revocationService,
    )

    @Bean("issuerKeyMaterial")
    fun issuerKeyMaterial(): KeyMaterial = when (configuration.issuerKey.type) {
        KeyType.FILE -> loadKeyFile(configuration.issuerKey.file!!, resourceLoader)
        KeyType.KEYSTORE -> loadKeyStore(configuration.issuerKey.keystore!!)
        KeyType.MEMORY -> EphemeralKeyWithSelfSignedCert()
    }

    @Bean("verifierKeyMaterial")
    fun verifierKeyMaterial(): KeyMaterial = when (configuration.verifierKey.type) {
        KeyType.FILE -> loadKeyFile(configuration.verifierKey.file!!, resourceLoader)
        KeyType.KEYSTORE -> loadKeyStore(configuration.verifierKey.keystore!!)
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
            keyStore = KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry("alias", privateKey, charArrayOf(), jcaCert?.let { arrayOf(it) })
            },
            keyAlias = "alias",
            privateKeyPassword = charArrayOf(),
            certAlias = jcaCert?.let { "alias" }
        )
    }

    private fun loadCertOrPubKey(
        publicKey: URI?,
        certificate: URI?,
        resourceLoader: ResourceLoader,
    ): Pair<PublicKey, java.security.cert.X509Certificate?> {
        if (publicKey == null && certificate == null)
            throw RuntimeException("Neither cert nor public key configured. Set one!")
        if (publicKey != null && certificate != null)
            throw RuntimeException("Both public key and certificate set. Set either but not both!")
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
        issuerKeyMaterial: KeyMaterial,
    ): Issuer = IssuerAgent(
        keyMaterial = issuerKeyMaterial,
        issuerCredentialStore = issuerCredentialStore,
        statusListBaseUrl = configuration.publicContext.appendPath(Paths.Credentials.StatusUrl),
        cryptoAlgorithms = setOf(issuerKeyMaterial.signatureAlgorithm),
        timePeriodProvider = timePeriodProvider(),
        identifier = UniformResourceIdentifier(configuration.publicContext.toString())
    )

    @Bean
    fun statusListIssuer(
        referencedTokenStore: ReferencedTokenStore,
        issuerKeyMaterial: KeyMaterial,
    ): StatusListIssuer = StatusListAgent(
        keyMaterial = issuerKeyMaterial,
        issuerCredentialStore = referencedTokenStore,
        statusListBaseUrl = configuration.publicContext.appendPath(Paths.Credentials.StatusUrl),
        statusListAggregationUrl = configuration.publicContext.appendPath(Paths.Credentials.Status.CurrentUrl),
        revocationListLifetime = configuration.revocationList.lifetimeDuration,
        timePeriodProvider = timePeriodProvider(),
    )

    @Bean
    fun timePeriodProvider(): TimePeriodProvider = FixedTimePeriodProvider

    private val credentialSchemes = setOf(
        EuPidScheme,
        EuPidSdJwtScheme,
        MobileDrivingLicenceScheme,
        PowerOfRepresentationScheme,
        CertificateOfResidenceScheme,
        TaxIdScheme,
        EhicScheme,
        AgeVerificationScheme
    )

    @Bean
    fun issuerService(
        authorizationServer: OAuth2AuthorizationServerAdapter,
        issuer: Issuer,
    ): CredentialIssuer = CredentialIssuer(
        publicContext = configuration.publicContext.toString(),
        credentialSchemes = credentialSchemes,
        authorizationService = authorizationServer,
        issuer = issuer,
        credentialEndpointPath = Paths.CredentialUrl,
        nonceEndpointPath = Paths.NonceUrl,
    )

    @Bean
    fun authorizationServer(
    ): SimpleAuthorizationService = SimpleAuthorizationService(
        strategy = CredentialAuthorizationServiceStrategy(credentialSchemes),
        publicContext = configuration.publicContext.toString(),
        authorizationEndpointPath = Paths.AuthorizeUrl,
        tokenEndpointPath = Paths.TokenUrl,
        pushedAuthorizationRequestEndpointPath = Paths.ParUrl,
        tokenService = TokenService.jwt(
            publicContext = configuration.publicContext.toString(),
        ),
    )

    @Bean
    fun messageConverter(): KotlinSerializationJsonHttpMessageConverter =
        KotlinSerializationJsonHttpMessageConverter(vckJsonSerializer)
}
