package at.asitplus.wallet.backend.config

import at.asitplus.KmmResult
import at.asitplus.catching
import at.asitplus.openid.IssuerMetadata
import at.asitplus.signum.indispensable.pki.X509Certificate as SignumX509Certificate
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.backend.AntilogSlf4jAdapter
import at.asitplus.wallet.backend.Extensions.appendPath
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.data.IdentityColumnResynchronizer
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.data.IssuerCredentialStoreAdapter
import at.asitplus.wallet.backend.data.RevokedCredentialRepository
import at.asitplus.wallet.backend.service.DefaultRevocationService
import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.eupid.EuPidItemValueSerializerMap
import at.asitplus.wallet.eupid.EuPidJsonValueEncoder
import at.asitplus.wallet.lib.LibraryInitializer
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.FixedTimePeriodProvider
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.agent.IssuerAgent
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.KeyStoreMaterial
import at.asitplus.wallet.lib.agent.StatusListAgent
import at.asitplus.wallet.lib.agent.TimePeriodProvider
import at.asitplus.wallet.lib.jws.JwsHeaderCertOrJwk
import at.asitplus.wallet.lib.jws.SignJwt
import at.asitplus.wallet.lib.jws.VerifyJwsObject
import at.asitplus.wallet.lib.jws.VerifyJwsObjectFun
import at.asitplus.wallet.lib.jws.VerifyJwsSignature
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.data.CredentialMetadataRegistry
import at.asitplus.wallet.lib.data.StaticCredentialMetadataRegistry
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.agents.ReferencedTokenStore
import at.asitplus.wallet.lib.data.rfc3986.UniformResourceIdentifier
import at.asitplus.wallet.lib.ktor.openid.RemoteCredentialMetadataRegistry
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oauth2.ClientAuthenticationService
import at.asitplus.wallet.lib.oauth2.AttestationBasedClientAuthenticationService
import at.asitplus.wallet.lib.oauth2.NoopClientAuthenticationService
import at.asitplus.wallet.lib.oauth2.TokenService
import at.asitplus.wallet.lib.oidvci.CredentialAuthorizationServiceStrategy
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.DefaultCredentialSchemeMapper
import at.asitplus.wallet.lib.oidvci.OAuth2AuthorizationServerAdapter
import at.asitplus.wallet.lib.oidvci.ProofValidator
import at.asitplus.wallet.mdl.MobileDrivingLicenceItemValueSerializerMap
import at.asitplus.wallet.mdl.MobileDrivingLicenceJsonValueEncoder
import at.asitplus.wallet.sdjwt.SdJwtTypeMetadataDocument
import at.asitplus.wallet.sdjwt.SdJwtTypeMetadataDocumentRegistry
import at.asitplus.wallet.sdjwt.SdJwtVcType
import io.github.aakira.napier.Napier
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.charset.Charset
import java.security.KeyStore
import java.security.PublicKey
import java.security.Security
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
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
    fun credentialMetadataRegistry(metadataHttpClient: HttpClient): CredentialMetadataRegistry {
        LibraryInitializer.registerCredentialSerializers(
            jsonValueEncoder = MobileDrivingLicenceJsonValueEncoder,
            itemValueSerializerMap = MobileDrivingLicenceItemValueSerializerMap,
        )
        LibraryInitializer.registerCredentialSerializers(
            jsonValueEncoder = EuPidJsonValueEncoder,
            itemValueSerializerMap = EuPidItemValueSerializerMap,
        )
        val remote = RemoteCredentialMetadataRegistry(
            httpClient = metadataHttpClient,
            clock = Clock.System,
            documentUrls = CredentialCatalog.documentUrls(),
            aliases = CredentialCatalog.aliases()
        )
        val local = StaticCredentialMetadataRegistry(
            documentRegistry = SdJwtTypeMetadataDocumentRegistry(
                CredentialCatalog.localEntries.associate {
                    SdJwtVcType(it.vct) to Json.decodeFromString<SdJwtTypeMetadataDocument>(
                        loadResource(resourceLoader, it.url)
                    )
                }
            ),
            documentUrls = CredentialCatalog.localEntries.associate { SdJwtVcType(it.vct) to it.url },
        )
        return object : CredentialMetadataRegistry {
            override fun preloadEntries() = local.preloadEntries()

            override suspend fun findEntry(identifier: String, representation: CredentialRepresentation) =
                local.findEntry(identifier, representation) ?: remote.findEntry(identifier, representation)
        }.also { LibraryInitializer.registerCredentialMetadataRegistry(it) }
    }

    @Bean
    fun revocationService(
        credentialRepo: IssuedCredentialRepository,
        revokedCredentialRepo: RevokedCredentialRepository,
        applicationEventPublisher: ApplicationEventPublisher,
        identityColumnResynchronizer: IdentityColumnResynchronizer,
    ): RevocationService = DefaultRevocationService(
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

    /**
     * One group per credential signing key: the [BackendConfigurationProperties.issuerKey] default plus one for every
     * entry in [BackendConfigurationProperties.credentialKeys]. Fails fast on an identifier this issuer does not offer,
     * or on two identifiers that would share a status list URL.
     */
    @Bean
    fun statusListGroups(
        referencedTokenStore: ReferencedTokenStore,
    ): StatusListGroups {
        requireKnownCredentialIdentifiers(
            configured = configuration.credentialKeys.keys,
            known = CredentialCatalog.entries.map { it.identifier }.toSet(),
        )
        val defaultStatusListGroup = buildStatusListGroup(null, configuration.issuerKey, referencedTokenStore)
        val statusListGroupsForIdentifiers = configuration.credentialKeys.map { (identifier, key) ->
            buildStatusListGroup(identifier, key, referencedTokenStore)
        }
        return StatusListGroups(listOf(defaultStatusListGroup) + statusListGroupsForIdentifiers)
    }

    private fun buildStatusListGroup(
        credentialIdentifier: String?,
        key: KeyConfiguration,
        referencedTokenStore: ReferencedTokenStore,
    ): StatusListGroup {
        val keyMaterial = loadKeyMaterial(key)
        val slug = credentialIdentifier?.toStatusListSlug() ?: ""
        return StatusListGroup(
            credentialIdentifier = credentialIdentifier,
            slug = slug,
            keyMaterial = keyMaterial,
            statusListAgent = StatusListAgent(
                keyMaterial = keyMaterial,
                issuerCredentialStore = referencedTokenStore,
                statusListBaseUrl = configuration.publicContext.appendPath(StatusListGroup.statusListPath(slug)),
                statusListAggregationUrl = configuration.publicContext.appendPath(Paths.Credentials.Status.CurrentUrl),
                revocationListLifetime = configuration.revocationList.lifetimeDuration,
                timePeriodProvider = timePeriodProvider(),
            ),
        )
    }

    @Bean
    fun issuerAgent(
        issuerCredentialStore: IssuerCredentialStore,
        statusListGroups: StatusListGroups,
    ): Issuer = SchemeRoutingIssuer(
        byCredentialIdentifier = statusListGroups.all.filterNot { it.isDefault }
            .associate { it.credentialIdentifier!! to buildIssuerAgent(issuerCredentialStore, it) },
        default = buildIssuerAgent(issuerCredentialStore, statusListGroups.default),
    )

    private fun buildIssuerAgent(
        issuerCredentialStore: IssuerCredentialStore,
        group: StatusListGroup,
    ): IssuerAgent = IssuerAgent(
        keyMaterial = group.keyMaterial,
        issuerCredentialStore = issuerCredentialStore,
        cryptoAlgorithms = setOf(group.keyMaterial.signatureAlgorithm),
        identifier = UniformResourceIdentifier(configuration.publicContext.toString()),
        statusListAgent = group.statusListAgent
    )

    @Bean
    fun timePeriodProvider(): TimePeriodProvider = FixedTimePeriodProvider

    /**
     * Resolved at boot from the type metadata documents (see [CredentialCatalog]), carrying display info for the
     * UI. The issuer and the authorization strategy still need the scheme set up front to build
     * `.well-known/openid-credential-issuer`; resolving via [AttributeIndex.resolveIdentifier] also registers each
     * scheme globally so the (synchronous) scheme mapper can decode credential identifiers later. Fails fast if a
     * document cannot be fetched.
     */
    @Bean
    fun credentialOfferings(
        credentialMetadataRegistry: CredentialMetadataRegistry,
    ): List<CredentialOffering> = CredentialCatalog.entries.map { doc ->
        val metadata =
            runBlocking { credentialMetadataRegistry.findEntry(doc.identifier, doc.representation)?.metadata }
        val scheme = runBlocking { AttributeIndex.resolveIdentifier(doc.identifier, doc.representation) }
        // findEntry returns null on load/integrity failure (resolveIdentifier then yields a fallback
        // scheme), so a non-null entry confirms the document actually resolved. vck deprecated
        // CredentialScheme.schemaUri, so we can no longer compare it against doc.url.
        require(metadata != null) {
            "Could not resolve metadata for ${doc.vct} from ${doc.url} " +
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
        statusListGroups: StatusListGroups,
        credentialOfferings: List<CredentialOffering>,
        walletProviderTrustService: WalletProviderTrustService,
    ): CredentialIssuer {
        val metadataSigner = SignJwt<IssuerMetadata>(
            configuration.metadataKey?.let(::loadKeyMaterial) ?: EphemeralKeyWithoutCert(),
            JwsHeaderCertOrJwk(),
        )
        val schemes = credentialOfferings.map { it.scheme }.toSet()
        // Every signing key must appear here, or wallets cannot verify credentials signed with it.
        val signingKeys = statusListGroups.all.map { it.keyMaterial }.toSet()
        return CredentialIssuer(
            publicContext = configuration.publicContext.toString(),
            credentialSchemes = schemes,
            authorizationService = authorizationServer,
            issuer = issuer,
            keyMaterial = signingKeys,
            credentialEndpointPath = Paths.CredentialUrl,
            nonceEndpointPath = Paths.NonceUrl,
            requireKeyAttestation = configuration.walletAttestation.enabled,
            proofValidator = credentialProofValidator(
                configuration.walletAttestation,
                configuration.publicContext.toString(),
                walletProviderTrustService,
            ),
            signMetadata = metadataSigner,
            credentialSchemeMapper = credentialSchemeMapper,
        )
    }

    @Bean
    fun authorizationServer(
        credentialOfferings: List<CredentialOffering>,
        walletProviderTrustService: WalletProviderTrustService,
    ): SimpleAuthorizationService {
        val clientAuthenticationService = walletClientAuthenticationService(
            configuration.walletAttestation,
            configuration.publicContext.toString(),
            walletProviderTrustService,
        )
        return SimpleAuthorizationService(
            strategy = CredentialAuthorizationServiceStrategy(
                credentialSchemes = credentialOfferings.map { it.scheme }.toSet(),
                mapper = credentialSchemeMapper
            ),
            publicContext = configuration.publicContext.toString(),
            authorizationEndpointPath = Paths.AuthorizeUrl,
            tokenEndpointPath = Paths.TokenUrl,
            pushedAuthorizationRequestEndpointPath = Paths.ParUrl,
            challengeEndpointPath = Paths.ChallengeUrl,
            tokenService = TokenService.jwt(publicContext = configuration.publicContext.toString()),
            clientAuthenticationService = clientAuthenticationService,
        )
    }

    @Bean
    fun walletProviderTrustService(): WalletProviderTrustService =
        WalletProviderTrustService(configuration.walletAttestation, resourceLoader)

    @Bean
    fun messageConverter(): KotlinSerializationJsonHttpMessageConverter =
        KotlinSerializationJsonHttpMessageConverter(joseCompliantSerializer)
}

/** Requires WIA client authentication against the current Wallet Provider and A-SIT anchors. */
internal fun walletClientAuthenticationService(
    configuration: WalletAttestationConfiguration,
    issuerIdentifier: String,
    trustService: WalletProviderTrustService,
): ClientAuthenticationService {
    if (!configuration.enabled) return NoopClientAuthenticationService

    return AttestationBasedClientAuthenticationService(
        issuerIdentifier = issuerIdentifier,
        // Bind the signature to the x5c leaf. The generic verifier may otherwise use a JWK asserted in the JWS.
        verifyJwsObject = trustedAttestationVerifier { trustService.walletProviderAnchors() },
    )
}

/**
 * In wallet attestation mode, credential proofs must carry a Key Attestation signed by a trusted Wallet Provider or
 * extra KA anchor. Otherwise Key Attestations are optional, and one that is present only needs a valid signature.
 */
internal fun credentialProofValidator(
    configuration: WalletAttestationConfiguration,
    issuerIdentifier: String,
    trustService: WalletProviderTrustService,
): ProofValidator = ProofValidator(
    publicContext = issuerIdentifier,
    requireKeyAttestation = configuration.enabled,
    verifyKeyAttestationSignature = if (configuration.enabled)
        trustedAttestationVerifier { trustService.keyAttestationAnchors() }
    else VerifyJwsObject(),
)

internal fun loadAttestationAnchors(locations: List<String>, resourceLoader: ResourceLoader): Set<X509Certificate> {
    val certificateFactory = CertificateFactory.getInstance("X.509")
    return locations.flatMap { location ->
        require(location.isNotBlank()) { "Attestation certificate location must not be blank" }
        resourceLoader.getResource(location).inputStream.use { stream ->
            certificateFactory.generateCertificates(stream).map { certificate ->
                certificate as X509Certificate
            }.also { require(it.isNotEmpty()) { "No attestation certificate in $location" } }
        }
    }.toSet()
}

internal fun trustedAttestationVerifier(anchors: suspend () -> Set<X509Certificate>) = VerifyJwsObjectFun { jws ->
    catching {
        val chain = jws.jwsHeader.certificateChain
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Attestation has no x5c")
        require(isTrustedAttestationChain(chain, anchors())) { "Attestation certificate is not trusted" }
        VerifyJwsSignature()(jws, chain.first().decodedPublicKey.getOrThrow()).getOrThrow()
    }
}

/** Accept a pinned leaf or a certificate path ending at a configured CA anchor. */
internal fun isTrustedAttestationChain(
    chain: List<SignumX509Certificate>,
    anchors: Set<X509Certificate>,
): Boolean = catching {
    require(chain.isNotEmpty() && anchors.isNotEmpty())
    val factory = CertificateFactory.getInstance("X.509")
    val presented = chain.map { signumCertificate ->
        factory.generateCertificate(ByteArrayInputStream(signumCertificate.encodeToDer())) as X509Certificate
    }
    presented.forEach { it.checkValidity() }
    anchors.forEach { it.checkValidity() }
    val leaf = presented.first()
    if (anchors.any { it.encoded.contentEquals(leaf.encoded) }) return@catching true

    val caAnchors = anchors.filter { it.basicConstraints >= 0 }.map { TrustAnchor(it, null) }.toSet()
    require(caAnchors.isNotEmpty())
    val pathCertificates = presented.takeWhile { certificate ->
        anchors.none { it.encoded.contentEquals(certificate.encoded) }
    }
    val path = factory.generateCertPath(pathCertificates)
    CertPathValidator.getInstance("PKIX").validate(
        path,
        PKIXParameters(caAnchors).apply { isRevocationEnabled = false },
    )
    true
}.getOrElse { false }

/**
 * Signs each credential with the key configured for it in [BackendConfigurationProperties.credentialKeys], falling back
 * to [default] for credentials without their own key.
 */
private class SchemeRoutingIssuer(
    private val byCredentialIdentifier: Map<String, Issuer>,
    private val default: Issuer,
) : Issuer {
    override val keyMaterial: KeyMaterial = default.keyMaterial
    override val cryptoAlgorithms =
        (byCredentialIdentifier.values + default).flatMap { it.cryptoAlgorithms }.toSet()

    override suspend fun issueCredential(credential: CredentialToBeIssued): KmmResult<Issuer.IssuedCredential> =
        (byCredentialIdentifier[credential.credentialIdentifier] ?: default).issueCredential(credential)
}

/**
 * The identifier this credential is configured under, matching [CredentialCatalog.Entry.identifier]. Dispatches on the
 * [CredentialToBeIssued] subtype rather than on `isoDocType ?: sdJwtType`, because a scheme such as EU PID implements
 * both interfaces and would otherwise collapse its SD-JWT and mdoc variants onto one key.
 */
private val CredentialToBeIssued.credentialIdentifier: String?
    get() = when (this) {
        is CredentialToBeIssued.Iso -> scheme.isoDocType
        is CredentialToBeIssued.VcSd -> scheme.sdJwtType
        is CredentialToBeIssued.VcJwt -> scheme.vcType
    }
