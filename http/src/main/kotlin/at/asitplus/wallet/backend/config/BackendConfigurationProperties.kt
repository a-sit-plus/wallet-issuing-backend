package at.asitplus.wallet.backend.config

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
    /**
     * Signing key per credential, keyed by `vct` (SD-JWT) or ISO docType (mdoc).
     * Credentials not listed here are signed with [issuerKey].
     */
    val credentialKeys: Map<String, KeyConfiguration> = emptyMap(),
    /** Key used for signing authn requests for PID login */
    val verifierKey: KeyConfiguration = KeyConfiguration(),
    /** Configure details about revocation lists. */
    val revocationList: RevocationListConfigurationProperties = RevocationListConfigurationProperties(),
    /** Issuer name for OID4VCI metadata. */
    val metadata: MetadataConfiguration = MetadataConfiguration(),
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
     * [group], resolving to the legacy path, so cache files written by earlier versions stay valid.
     */
    fun cwtPath(group: String = "") = basePath + "cwt/" + if (group.isEmpty()) "" else "$group/"

    /** Cache directory for the JWT status list tokens of one status list group, see [cwtPath]. */
    fun jwtPath(group: String = "") = basePath + "jwt/" + if (group.isEmpty()) "" else "$group/"
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
