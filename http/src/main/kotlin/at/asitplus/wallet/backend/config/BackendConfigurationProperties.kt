package at.asitplus.wallet.backend.config

import at.asitplus.wallet.backend.TimeSource
import at.asitplus.wallet.lib.agent.MonthAndDay
import at.asitplus.wallet.lib.agent.RevocationListCache
import kotlinx.datetime.Month
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.net.URI
import kotlin.time.Duration

@ConfigurationProperties(prefix = "backend")
@ConstructorBinding
data class BackendConfigurationProperties(
    /**
     * Public URL of this instance, used for several URLs in messages sent to the Wallet
     */
    val publicContext: String = "http://localhost:8080/",

    val timeSource: TimeSource = TimeSource.SYSTEM,

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
) {
    val schooYearStart: MonthAndDay =
        timePeriodRollover.split('-').let { Month.of(it[0].toInt()) to it[1].toUByte() }
}

@ConstructorBinding
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

@ConstructorBinding
data class CredentialConfigurationProperties(
    /**
     * Lifetime of the credentials issued, e.g. 60 minutes (`PT6M`) or 180 days (`P180D`)
     */
    private val lifetime: String = "PT6M",
    /**
     * Whether to revoke all existing credentials when a new credential is issued for the same device binding
     */
    val oneCredentialPerDeviceBinding: Boolean = true,

    val revocationListCache: RevocationListCacheProperties? = null,

    /**
     * Additional validity period added on top of issued credential validity . Default:  or 90 days (`P90D`)
     */
    private val gracePeriod: String = "P90D",
) {
    //eager evaluation → fail on load
    val lifeTime: Duration = Duration.parse(lifetime)
    val gracePeriodDuration = Duration.parse(gracePeriod)
}

@ConstructorBinding
data class RevocationListCacheProperties(
    private val min: String,
    private val max: String,
) {
    val cacheDuration: RevocationListCache = Duration.parse(min) to Duration.parse(max)
}

@ConstructorBinding
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

@ConstructorBinding
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

@ConstructorBinding
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

@ConstructorBinding
data class AttributeSourceConfigurationProperties(
    /**
     * Type of the attribute source
     */
    val type: AttributeSourceType = AttributeSourceType.RANDOM,
    /**
     * Default for PupilId: Get attributes from ECO ("edu.card online")
     */
    val eco: EcoAttributeSourceConfigurationProperties? = null,
    /**
     * Generate random values for attributes
     */
    val random: RandomAttributeSourceConfigurationProperties? = RandomAttributeSourceConfigurationProperties()
)

enum class AttributeSourceType {
    RANDOM,
    ECO,
    EIDAS
}

@ConstructorBinding
data class EcoAttributeSourceConfigurationProperties(
    override val url: URI? = null,
    override val clientTls: Boolean = false,
    override val serverTls: Boolean = true,
    override val key: KeyConfiguration? = null,
    override val trust: TrustConfiguration? = null,
    override val httpBasic: HttpBasicAuthnConfigurationProperties? = null,
    override val apiKey: String? = null
) : ExternalConnectionConfig

interface ExternalConnectionConfig {
    val url: URI?
    val clientTls: Boolean
    val serverTls: Boolean
    val key: KeyConfiguration?
    val trust: TrustConfiguration?
    val httpBasic: HttpBasicAuthnConfigurationProperties?
    val apiKey: String?
}

@ConstructorBinding
data class RandomAttributeSourceConfigurationProperties(
    /**
     * Location of random photos to be used for issued credentials
     */
    val photoLocation: URI = URI.create("file:photos/"),
)

@ConstructorBinding
data class HttpBasicAuthnConfigurationProperties(
    val username: String,
    val password: String,
)

@ConstructorBinding
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

@ConstructorBinding
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

@ConstructorBinding
data class InternalPkiConfigurationProperties(
    val issuerName: String = "CN=WalletBackend",
    val key: KeyConfiguration = KeyConfiguration(),
)

@ConstructorBinding
data class AeraPkiConfigurationProperties(
    override val url: URI? = null,
    override val clientTls: Boolean = false,
    override val serverTls: Boolean = true,
    override val key: KeyConfiguration? = null,
    override val trust: TrustConfiguration? = null,
    override val httpBasic: HttpBasicAuthnConfigurationProperties? = null,
    override val apiKey: String? = null
) : ExternalConnectionConfig

@ConstructorBinding
data class DeviceBindingConfigurationProperties(
    val type: DeviceBindingNonceType = DeviceBindingNonceType.INTERNAL,
    val eco: EcoDeviceBindingConfigurationProperties = EcoDeviceBindingConfigurationProperties(),
    val attestation: AttestationConfigurationProperties = AttestationConfigurationProperties()
)

enum class DeviceBindingNonceType {
    INTERNAL,
    ECO
}

@ConstructorBinding
data class AttestationConfigurationProperties(
    val android: AndroidAttestationConfigurationProperties = AndroidAttestationConfigurationProperties(),
    val ios: IOSAttestationConfigurationProperties = IOSAttestationConfigurationProperties(),
)

@ConstructorBinding
data class AndroidAttestationConfigurationProperties(
    val packageName: String="com.apple.dollars",
    val applicationVersion: Int? = null,
    val androidVersion: Int? = 10000,
    val patchLevel: PatchLevelConfigurationProperties? = PatchLevelConfigurationProperties(2021, 8),
    val signatureDigest: String="DEADBEEF",
    val requireStrongBox: Boolean = false,
    val requireRollbackResistance: Boolean = true,
)

@ConstructorBinding
data class IOSAttestationConfigurationProperties(
    val teamIdentifier: String="0000000000",
    val bundleIdentifier: String="com.google.dollars",
    val devStage: Boolean = false,
    val kid: String="Lg==",
    val iosVersion: String?=null,
)

@ConstructorBinding
data class PatchLevelConfigurationProperties(val year: Int, val month: Int)

@ConstructorBinding
data class EcoDeviceBindingConfigurationProperties(
    override val url: URI? = null,
    override val clientTls: Boolean = false,
    override val serverTls: Boolean = true,
    override val key: KeyConfiguration? = null,
    override val trust: TrustConfiguration? = null,
    override val httpBasic: HttpBasicAuthnConfigurationProperties? = null,
    override val apiKey: String? = null
) : ExternalConnectionConfig

@ConstructorBinding
data class ApiKeyConfigurationProperties(
    val name: String,
    val key: String,
)

@ConstructorBinding
data class KeyConfiguration(
    val type: KeyType = KeyType.MEMORY,
    val file: KeyFileConfiguration? = null,
    val keystore: KeyStoreConfiguration? = null,
    val hsmfacade: KeyHsmFacadeConfiguration? = null,
    val remote: KeyRemoteCryptoConfiguration? = null,
)

@ConstructorBinding
data class KeyFileConfiguration(
    val privateKey: URI,
    val publicKey: URI?,
    val certificate: URI?,
)

@ConstructorBinding
data class KeyStoreConfiguration(
    val path: URI,
    val type: String,
    val provider: String? = null,
    val password: String? = null,
    val alias: String,
    val aliasPassword: String? = null,
)

@ConstructorBinding
data class KeyHsmFacadeConfiguration(
    val keyStoreName: String? = null,
    val keyStoreAlias: String? = null,
)

@ConstructorBinding
data class KeyRemoteCryptoConfiguration(
    val keyName: String,
    val publicKey: URI?,
    val certificate: URI?,
)

@ConstructorBinding
data class TrustConfiguration(
    val type: TrustType = TrustType.SYSTEM,
    val truststore: TrustStoreConfiguration? = null,
)

@ConstructorBinding
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