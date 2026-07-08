package at.asitplus.wallet.backend.config

import at.asitplus.KmmResult
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
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
import at.asitplus.wallet.eupid.EuPidItemValueSerializerMap
import at.asitplus.wallet.eupid.EuPidJsonValueEncoder
import at.asitplus.wallet.lib.LibraryInitializer
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
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
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.agents.ReferencedTokenStore
import at.asitplus.wallet.lib.data.rfc3986.UniformResourceIdentifier
import at.asitplus.wallet.lib.ktor.openid.RemoteCredentialMetadataRegistry
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oauth2.TokenService
import at.asitplus.wallet.lib.oidvci.CredentialAuthorizationServiceStrategy
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.DefaultCredentialSchemeMapper
import at.asitplus.wallet.lib.oidvci.OAuth2AuthorizationServerAdapter
import at.asitplus.wallet.mdl.MobileDrivingLicenceItemValueSerializerMap
import at.asitplus.wallet.mdl.MobileDrivingLicenceJsonValueEncoder
import io.github.aakira.napier.Napier
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
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
import kotlin.time.Clock

@Configuration
@EnableConfigurationProperties(value = [BackendConfigurationProperties::class])
@EnableScheduling
class BackendConfiguration {

    @Autowired
    private lateinit var configuration: BackendConfigurationProperties

    @Autowired
    private lateinit var resourceLoader: ResourceLoader


    /**
     * Fetches the SD-JWT Type Metadata documents live from the hosted collection. Tests override this with a
     * [io.ktor.client.engine.mock.MockEngine] serving the cached documents from test resources
     * (see `CachedTypeMetadataConfiguration` in the test sources), so no test ever talks to GitHub.
     */
    @Bean
    fun metadataHttpClient(): HttpClient = HttpClient()


    init {
        Napier.takeLogarithm()
        Napier.base(AntilogSlf4jAdapter())
        Security.addProvider(BouncyCastleProvider())
    }

    @Bean
    fun credentialMetadataRegistry(metadataHttpClient: HttpClient): RemoteCredentialMetadataRegistry {
        LibraryInitializer.registerCredentialSerializers(
            jsonValueEncoder = MobileDrivingLicenceJsonValueEncoder,
            itemValueSerializerMap = MobileDrivingLicenceItemValueSerializerMap,
        )
        LibraryInitializer.registerCredentialSerializers(
            jsonValueEncoder = EuPidJsonValueEncoder,
            itemValueSerializerMap = EuPidItemValueSerializerMap,
        )
        return RemoteCredentialMetadataRegistry(
            httpClient = metadataHttpClient,
            clock = Clock.System,
            documentUrls = CredentialCatalog.documentUrls(),
            aliases = CredentialCatalog.aliases()
        ).also { LibraryInitializer.registerCredentialMetadataRegistry(it) }
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
    fun issuerKeyMaterial(): KeyMaterial = loadKeyMaterial(configuration.issuerKey)

    @Bean("isoMdocIssuerKeyMaterial")
    fun isoMdocIssuerKeyMaterial(
        @Qualifier("issuerKeyMaterial") issuerKeyMaterial: KeyMaterial,
    ): KeyMaterial = configuration.isoMdocIssuerKey?.let { loadKeyMaterial(it) } ?: issuerKeyMaterial

    @Bean("verifierKeyMaterial")
    fun verifierKeyMaterial(): KeyMaterial = loadKeyMaterial(configuration.verifierKey)

    private fun loadKeyMaterial(config: KeyConfiguration): KeyMaterial = when (config.type) {
        KeyType.FILE -> loadKeyFile(config.file!!, resourceLoader)
        KeyType.KEYSTORE -> loadKeyStore(config.keystore!!)
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
        @Qualifier("issuerKeyMaterial") issuerKeyMaterial: KeyMaterial,
        @Qualifier("isoMdocIssuerKeyMaterial") isoMdocIssuerKeyMaterial: KeyMaterial,
    ): Issuer = IsoMdocRoutingIssuer(
        defaultIssuer = buildIssuerAgent(issuerCredentialStore, issuerKeyMaterial),
        isoMdocIssuer = buildIssuerAgent(issuerCredentialStore, isoMdocIssuerKeyMaterial),
    )

    private fun buildIssuerAgent(
        issuerCredentialStore: IssuerCredentialStore,
        keyMaterial: KeyMaterial,
    ): IssuerAgent = IssuerAgent(
        keyMaterial = keyMaterial,
        issuerCredentialStore = issuerCredentialStore,
        statusListBaseUrl = configuration.publicContext.appendPath(Paths.Credentials.StatusUrl),
        cryptoAlgorithms = setOf(keyMaterial.signatureAlgorithm),
        timePeriodProvider = timePeriodProvider(),
        identifier = UniformResourceIdentifier(configuration.publicContext.toString())
    )

    @Bean
    fun statusListIssuer(
        referencedTokenStore: ReferencedTokenStore,
        @Qualifier("issuerKeyMaterial") issuerKeyMaterial: KeyMaterial,
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

    /**
     * Resolved at boot from the remote type metadata documents (see [CredentialCatalog]), carrying display info for the
     * UI. The issuer and the authorization strategy still need the scheme set up front to build
     * `.well-known/openid-credential-issuer`; resolving via [AttributeIndex.resolveIdentifier] also registers each
     * scheme globally so the (synchronous) scheme mapper can decode credential identifiers later. Fails fast if a
     * document cannot be fetched.
     */
    @Bean
    fun credentialOfferings(
        credentialMetadataRegistry: RemoteCredentialMetadataRegistry,
    ): List<CredentialOffering> = CredentialCatalog.entries.map { doc ->
        val metadata =
            runBlocking { credentialMetadataRegistry.findEntry(doc.identifier, doc.representation)?.metadata }
        val scheme = runBlocking { AttributeIndex.resolveIdentifier(doc.identifier, doc.representation) }
        // findEntry returns null on fetch/integrity failure (resolveIdentifier then yields a fallback
        // scheme), so a non-null entry confirms the remote document actually resolved. vck deprecated
        // CredentialScheme.schemaUri, so we can no longer compare it against doc.url.
        require(metadata != null) {
            "Could not resolve remote metadata for ${doc.vct} from ${doc.url} " +
                    "(got ${scheme::class.simpleName})"
        }
        CredentialOffering(
            scheme,
            doc.representation,
            metadata.displayName(doc.vct),
            metadata.displayDescription()
        )
    }

    private val credentialSchemeMapper = FixedAvCredentialSchemeMapper(
        delegate = DefaultCredentialSchemeMapper(),
        fixedIdentifier = "proof_of_age" // per AV profile
    )

    @Bean
    fun issuerService(
        authorizationServer: OAuth2AuthorizationServerAdapter,
        issuer: Issuer,
        @Qualifier("issuerKeyMaterial") issuerKeyMaterial: KeyMaterial,
        @Qualifier("isoMdocIssuerKeyMaterial") isoMdocIssuerKeyMaterial: KeyMaterial,
        credentialOfferings: List<CredentialOffering>,
    ): CredentialIssuer = CredentialIssuer(
        publicContext = configuration.publicContext.toString(),
        credentialSchemes = credentialOfferings.map { it.scheme }.toSet(),
        authorizationService = authorizationServer,
        issuer = issuer,
        keyMaterial = setOf(issuerKeyMaterial, isoMdocIssuerKeyMaterial),
        credentialEndpointPath = Paths.CredentialUrl,
        nonceEndpointPath = Paths.NonceUrl,
        credentialSchemeMapper = credentialSchemeMapper,
    )

    @Bean
    fun authorizationServer(
        credentialOfferings: List<CredentialOffering>,
    ): SimpleAuthorizationService = SimpleAuthorizationService(
        strategy = CredentialAuthorizationServiceStrategy(
            credentialSchemes = credentialOfferings.map { it.scheme }.toSet(),
            mapper = credentialSchemeMapper
        ),
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
        KotlinSerializationJsonHttpMessageConverter(joseCompliantSerializer)
}

private class IsoMdocRoutingIssuer(
    private val defaultIssuer: Issuer,
    private val isoMdocIssuer: Issuer,
) : Issuer {
    override val keyMaterial: KeyMaterial = defaultIssuer.keyMaterial
    override val cryptoAlgorithms = defaultIssuer.cryptoAlgorithms + isoMdocIssuer.cryptoAlgorithms

    override suspend fun issueCredential(credential: CredentialToBeIssued): KmmResult<Issuer.IssuedCredential> =
        when (credential) {
            is CredentialToBeIssued.Iso -> isoMdocIssuer.issueCredential(credential)
            is CredentialToBeIssued.VcJwt,
            is CredentialToBeIssued.VcSd,
                -> defaultIssuer.issueCredential(credential)
        }
}
