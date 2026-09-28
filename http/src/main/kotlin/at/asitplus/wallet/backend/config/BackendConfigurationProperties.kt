package at.asitplus.wallet.backend.config

import at.asitplus.wallet.lib.etsi.LoTEServiceType
import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.net.URL
import kotlin.time.Duration

@ConfigurationProperties(prefix = "backend")
data class BackendConfigurationProperties(
    /** Public URL of this instance, used for several URLs in messages sent to the Wallet. */
    val publicContext: URL = URL("http://localhost:8080"),
    /** Configuration for issued credentials. */
    val credentials: CredentialConfigurationProperties = CredentialConfigurationProperties(),
    /** Key used for signing issued credentials, and fallback for credentials absent from [credentialKeys]. */
    val issuerKey: KeyConfiguration = KeyConfiguration(),
    /** Optional key and certificate chain for JWT-signed issuer metadata (WRPAC). */
    val metadataKey: KeyConfiguration? = null,
    /**
     * Signing key per credential, keyed by `vct` (SD-JWT) or ISO docType (mdoc).
     * Credentials not listed here are signed with [issuerKey].
     */
    val credentialKeys: Map<String, KeyConfiguration> = emptyMap(),
    /** Key used for signing authn requests for PID login */
    val verifierKey: KeyConfiguration = KeyConfiguration(),
    /** Optional HAIP client authentication using Wallet Instance Attestations. */
    val walletAttestation: WalletAttestationConfiguration = WalletAttestationConfiguration(),
    /** Configure details about revocation lists. */
    val revocationList: RevocationListConfigurationProperties = RevocationListConfigurationProperties(),
    /** Issuer name for OID4VCI metadata. */
    val metadata: MetadataConfiguration = MetadataConfiguration(),
)

data class WalletAttestationConfiguration(
    /** When enabled, PAR and token requests require a WIA signed by a trusted Wallet Provider. */
    val enabled: Boolean = false,
    /** Wallet Provider LoTE URLs from the Commission acceptance and development environments. */
    val walletProviderLoteUrls: List<String> = listOf(
        LoTEServiceType.WALLET.defaultUrl(),
        LoTEServiceType.WALLET.defaultUrl("https://development.trust.tech.ec.europa.eu/lists/eudiw"),
    ),
    /** Optional PEM anchors for the LoTE JWS signer. If absent, trust relies on HTTPS and JAdES verification. */
    val loteSignerCertificates: List<String> = emptyList(),
    /** Additional PEM-encoded Wallet Provider certificates or trust anchors. */
    val trustedCertificates: List<String> = emptyList(),
    /** Additional trust anchors for Key Attestation signatures. */
    val trustedKeyAttestationCertificates: List<String> = emptyList(),
)

data class MetadataConfiguration(
    val name: String = "A-SIT Plus Wallet Issuer",
    val logo: String = "https://wallet.a-sit.plus/assets/images/logo.svg",
)

data class CredentialConfigurationProperties(
    /** Lifetime of the credentials issued, defaults to `P7D`. */
    private val lifetime: String = "P7D",
) {
    //eager evaluation → fail on load
    val lifeTime: Duration = Duration.parse(lifetime)
}

data class RevocationListConfigurationProperties(
    /** Lifetime of a single revocation list, defaults to `P7D`, i.e. 7 days. */
    private val lifetime: String = "P7D",
    /** Timeout after which to write revocation lists again, that have not been written recently, defaults to `P5D`. */
    private val regularWriteTimeout: String = "P5D",
    /**
     * Rate at which to check for dirty revocation lists that shall be written after a credential got revoked,
     * defaults to `PT10M`.
     */
    private val dirtyCheckRate: String = "PT10M",
    /**
     * Rate at which to check for outdated revocation lists that shall be written again, if nothing changed,
     * defaults to `PT1H`.
     */
    private val regularCheckRate: String = "PT1H",
    /**
     * Path at which the revocation lists shall be written to and read from, defaults to `cache/revocation-lists/`
     */
    val path: String = "cache/revocation-lists/",
) {
    val lifetimeDuration: Duration = Duration.parse(lifetime)
    val regularWriteTimeoutDuration: Duration = Duration.parse(regularWriteTimeout)
    val dirtyCheckRateDuration: Duration = Duration.parse(dirtyCheckRate)
    val regularCheckRateDuration: Duration = Duration.parse(regularCheckRate)
    private val basePath = path.let { if (it.endsWith("/")) it else "$it/" }

    /**
     * Cache directory for the CWT status list tokens of one status list group. The default group passes an empty
     * [slug], resolving to the legacy path, so cache files written by earlier versions stay valid.
     */
    fun cwtPath(slug: String = "") = basePath + "cwt/" + if (slug.isEmpty()) "" else "$slug/"

    /** Cache directory for the JWT status list tokens of one status list group, see [cwtPath]. */
    fun jwtPath(slug: String = "") = basePath + "jwt/" + if (slug.isEmpty()) "" else "$slug/"
}

data class KeyConfiguration(
    val type: KeyType = KeyType.MEMORY,
    val file: KeyFileConfiguration? = null,
    val keystore: KeyStoreConfiguration? = null,
)

data class KeyFileConfiguration(
    val privateKey: URI,
    val publicKey: URI?,
    val certificate: URI?,
)

data class KeyStoreConfiguration(
    val path: URI,
    val type: String,
    val provider: String? = null,
    val password: String? = null,
    val alias: String,
    val aliasPassword: String? = null,
)

enum class KeyType {
    FILE,
    MEMORY,
    KEYSTORE,
}
