package at.asitplus.wallet.backend.config

import at.asitplus.attestation.IOSAttestationConfiguration
import at.asitplus.wallet.pupilid.MonthAndDay
import kotlinx.datetime.Month
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@ConfigurationProperties(prefix = "backend")
data class BackendConfigurationProperties(
    /**
     * Public URL of this instance, used for several URLs in messages sent to the Wallet
     */
    val publicContext: String = "http://localhost:8080/",
    /**
     * Date when a school year starts (MM-DD)
     */
    private val timePeriodRollover: String = "09-10",
    /**
     * Configuration for issued credentials
     */
    val credentials: CredentialConfigurationProperties = CredentialConfigurationProperties(),
    /**
     * Configuration for automatic cleanup of expired bindings, and credentials
     */
    val cleanup: CleanupConfigurationProperties = CleanupConfigurationProperties(),
    /**
     * Key used for signing issued credentials
     */
    val issuerKey: KeyConfiguration = KeyConfiguration(),
    /**
     * General connection information to HSM Facade
     */
    val hsmfacade: HsmFacadeConfiguration = HsmFacadeConfiguration(),
    /**
     * General connection information to Remote Crypto Service
     */
    val remoteCrypto: RemoteCryptoConfiguration = RemoteCryptoConfiguration(),
    /**
     * Configure debug endpoints
     */
    val debug: DebugConfigurationProperties = DebugConfigurationProperties(),
    /**
     * Configure authentication of external services at this service
     */
    val authn: AuthnConfigurationProperties = AuthnConfigurationProperties(),
    /**
     * Configure PKI service for signing and revoking Device Bindings
     */
    val pki: PkiConfigurationProperties = PkiConfigurationProperties(),
    /**
     * Configure the source of attributes for the credentials
     */
    val attributeSource: AttributeSourceConfigurationProperties = AttributeSourceConfigurationProperties(),
    /**
     * Configure details about revocation lists for Verifiable Credentials
     */
    val revocationList: RevocationListConfigurationProperties = RevocationListConfigurationProperties(),
) {
    val schoolYearStart: MonthAndDay =
        timePeriodRollover.split('-').let { Month.of(it[0].toInt()) to it[1].toUByte() }
}

data class DebugConfigurationProperties(
    /**
     * Whether to enable debug endpoints at all
     */
    val enabled: Boolean = false,
    /**
     * Size of QR Codes, displayed only on debug endpoints
     */
    val qrCodeSize: Int = 400,
)

data class CredentialConfigurationProperties(
    /**
     * Lifetime of the credentials issued, e.g. 60 minutes (`PT6M`) or 180 days (`P180D`)
     */
    private val lifetime: String = "PT6M",
    /**
     * Whether to revoke all existing credentials when a new credential is issued for the same device binding
     */
    val oneCredentialPerDeviceBinding: Boolean = true,
    /**
     * Settings for scaling and compressing pictures in credentials
     */
    val pictures: PicturesConfigurationProperties = PicturesConfigurationProperties()
) {
    //eager evaluation → fail on load
    val lifeTime: Duration = Duration.parse(lifetime)
}

data class PicturesConfigurationProperties(
    /**
     * Whether to compress the pictures at all. Default: `true`.
     */
    val compress: Boolean = true,
    /**
     * Format of the compressed picture. Default: `webp`.
     */
    val format: String = "webp",
    /**
     * Quality of the compressed picture. Default: `30`.
     */
    val quality: Int = 30,
    /**
     * Whether to scale the pictures at all. Default: `true`.
     */
    val scale: Boolean = true,
    /**
     * Height of the scaled picture. Default: `154`.
     */
    val height: Int = 154,
    /**
     * Width of the scaled picture. Default: `120`.
     */
    val width: Int = 120,
    /**
     * Path to library `libwebp_jni.so`. Default: `null`, meaning load from system paths.
     */
    val pathLibJni: String? = null,
    /**
     * Path to library `libwebp.so.7`. Default: `null`, meaning load from system paths.
     */
    val pathLibWebp: String? = null,
    /**
     * Path to library `libsharpyuv.so.0`. Default: `null`, meaning load from system paths.
     */
    val pathLibSharp: String? = null,
)

data class CleanupConfigurationProperties(
    /**
     * Whether to enable the cleanup at all
     */
    val enabled: Boolean = false,
    /**
     * Rate at which expired bindings shall be deleted
     */
    val bindingsSchedulingRate: String = "PT24H",
    /**
     * Timespan in days after which an expired binding shall be deleted
     */
    val bindingsExpirationDays: Int = 30,
    /**
     * Rate at which expired credentials shall be deleted
     */
    val credentialsSchedulingRate: String = "PT24H",
    /**
     * Timespan in days after which an expired credential shall be deleted
     */
    val credentialsExpirationDays: Int = 30,
) {
    val bindingsSchedulingRateDuration = Duration.parse(bindingsSchedulingRate)
    val credentialsSchedulingRateDuration = Duration.parse(credentialsSchedulingRate)
}

data class HsmFacadeConfiguration(
    /**
     * Whether to enable connection to the HsmFacade service at all
     */
    val enabled: Boolean = false,
    /**
     * TLS root certificate used by the HsmFacade service, pinned here
     */
    val rootCertificate: URI? = null,
    /**
     * Host to connect to
     */
    val hostname: String? = null,
    /**
     * Port used for the connection
     */
    val port: Int? = null,
    /**
     * Username for authentication
     */
    val username: String? = null,
    /**
     * Password for authentication
     */
    val password: String? = null,
    /**
     * Timeout for one call to the HsmFacade service in seconds
     */
    val timeout: Long = 30,
)

data class RemoteCryptoConfiguration(
    /**
     * Whether to enable connection to the Remote Crypto service at all
     */
    val enabled: Boolean = false,
    /**
     * Host to connect to
     */
    val hostname: String? = null,
    /**
     * Port used for the connection
     */
    val port: Int? = null,
    /**
     * Username for authentication
     */
    val apiKey: String? = null
)

data class RevocationListConfigurationProperties(
    /**
     * Lifetime of a single revocation list, defaults to `P7D`, i.e. 7 days.
     */
    private val lifetime: String = "P7D",
    /**
     * Timeout after which to write revocation lists again, that have not been written recently, defaults to `P5D`.
     */
    private val regularWriteTimeout: String = "P5D",
    /**
     * Rate at which to check for dirty revocation lists that shall be written after a credential got revoked, defaults to `PT10M`.
     */
    private val dirtyCheckRate: String = "PT10M",
    /**
     * Rate at which to check for outdated revocation lists that shall be written again, if nothing changed, defaults to `PT1H`.
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
}

data class AttributeSourceConfigurationProperties(
    /**
     * Type of the attribute source
     */
    val type: AttributeSourceType = AttributeSourceType.RANDOM,
    /**
     * Generate random values for attributes
     */
    val random: RandomAttributeSourceConfigurationProperties? = RandomAttributeSourceConfigurationProperties()
)

enum class AttributeSourceType {
    RANDOM,
    EIDAS
}

interface ExternalConnectionConfig {
    val url: URI?
    val clientTls: Boolean
    val serverTls: Boolean
    val key: KeyConfiguration?
    val trust: TrustConfiguration?
    val httpBasic: HttpBasicAuthnConfigurationProperties?
    val apiKey: String?
}

data class RandomAttributeSourceConfigurationProperties(
    /**
     * Location of random photos to be used for issued credentials
     */
    val photoLocation: URI = URI.create("file:photos/"),
)

data class HttpBasicAuthnConfigurationProperties(
    val username: String,
    val password: String,
)

data class AuthnConfigurationProperties(
    /**
     * Lifetime of challenges sent to clients, e.g. during device binding
     */
    val challengeTimeoutSeconds: Int = 60,
    /**
     * Valid API keys for revocation endpoints
     */
    val apiKeys: Collection<ApiKeyConfigurationProperties> = listOf(),
    /**
     * Configuration for nonces used during device binding
     */
    val deviceBinding: DeviceBindingConfigurationProperties = DeviceBindingConfigurationProperties(),
)

data class PkiConfigurationProperties(
    val type: PkiType = PkiType.INTERNAL,
    val internal: InternalPkiConfigurationProperties = InternalPkiConfigurationProperties(),
    val aera: AeraPkiConfigurationProperties = AeraPkiConfigurationProperties(),
    val certValidityDays: Int = 182,
)

enum class PkiType {
    INTERNAL,
    PERSISTENT,
    AERA
}

data class InternalPkiConfigurationProperties(
    val issuerName: String = "CN=WalletBackend",
    val key: KeyConfiguration = KeyConfiguration(),
)

data class AeraPkiConfigurationProperties(
    override val url: URI? = null,
    override val clientTls: Boolean = false,
    override val serverTls: Boolean = true,
    override val key: KeyConfiguration? = null,
    override val trust: TrustConfiguration? = null,
    override val httpBasic: HttpBasicAuthnConfigurationProperties? = null,
    override val apiKey: String? = null
) : ExternalConnectionConfig

data class DeviceBindingConfigurationProperties(
    val type: DeviceBindingNonceType = DeviceBindingNonceType.INTERNAL,
    val attestation: AttestationConfigurationProperties = AttestationConfigurationProperties(),
)

enum class DeviceBindingNonceType {
    INTERNAL
}

data class AttestationConfigurationProperties(
    val android: AndroidAttestationConfigurationProperties? = null,
    val ios: IOSAttestationConfigurationProperties? = null,
    val noop: Boolean? = null,
    private val verificationOffset: String? = null,
) {
    val verificationOffSetDuration = verificationOffset?.let { Duration.parse(it) } ?: 0.seconds
}

data class AndroidAttestationConfigurationProperties(
    val packageName: String,
    val applicationVersion: Int? = null,
    val androidVersion: Int? = 10000,
    val patchLevel: PatchLevelConfigurationProperties? = PatchLevelConfigurationProperties(2021, 8),
    val signatureDigests: Array<String>,
    val requireStrongBox: Boolean = false,
    val requireRollbackResistance: Boolean = false,
    val ignoreLeafValidity: Boolean = false,
)

data class IOSAttestationConfigurationProperties(
    val teamIdentifier: String,
    val bundleIdentifier: String,
    val sandbox: Boolean = false,
    val iosVersion: String? = null,
) {
    fun toIosAttestationConfiguration() =
        IOSAttestationConfiguration(teamIdentifier, bundleIdentifier, sandbox, iosVersion)
}

data class PatchLevelConfigurationProperties(val year: Int, val month: Int)

data class ApiKeyConfigurationProperties(
    val name: String,
    val key: String,
)

data class KeyConfiguration(
    val type: KeyType = KeyType.MEMORY,
    val file: KeyFileConfiguration? = null,
    val keystore: KeyStoreConfiguration? = null,
    val hsmfacade: KeyHsmFacadeConfiguration? = null,
    val remote: KeyRemoteCryptoConfiguration? = null,
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

data class KeyHsmFacadeConfiguration(
    val keyStoreName: String? = null,
    val keyStoreAlias: String? = null,
)

data class KeyRemoteCryptoConfiguration(
    val keyName: String,
    val publicKey: URI?,
    val certificate: URI?,
)

data class TrustConfiguration(
    val type: TrustType = TrustType.SYSTEM,
    val truststore: TrustStoreConfiguration? = null,
)

data class TrustStoreConfiguration(
    val path: URI,
    val type: String,
    val provider: String? = null,
    val password: String? = null,
)

enum class KeyType {
    FILE,
    MEMORY,
    KEYSTORE,
    HSMFACADE,
    REMOTE,
}

enum class TrustType {
    SYSTEM,
    KEYSTORE,
}
