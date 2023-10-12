package at.asitplus.wallet.backend.config

import at.asitplus.attestation.IOSAttestationConfiguration
import at.asitplus.wallet.pupilid.MonthAndDay
import kotlinx.datetime.Month
import org.springframework.boot.context.properties.ConfigurationProperties
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
     * Configuration for issued credentials
     */
    val credentials: CredentialConfigurationProperties = CredentialConfigurationProperties(),
    /**
     * Key used for signing issued credentials
     */
    val issuerKey: KeyConfiguration = KeyConfiguration(),
    /**
     * General connection information to HSM Facade
     */
    val hsmfacade: HsmFacadeConfiguration = HsmFacadeConfiguration(),
    /**
     * Configure debug endpoints
     */
    val debug: DebugConfigurationProperties = DebugConfigurationProperties(),
    /**
     * Configure authentication of external services at this service
     */
    val authn: AuthnConfigurationProperties = AuthnConfigurationProperties(),
    /**
     * Configure the source of attributes for the credentials
     */
    val attributeSource: AttributeSourceConfigurationProperties = AttributeSourceConfigurationProperties(),
    /**
     * Configure details about revocation lists for Verifiable Credentials
     */
    val revocationList: RevocationListConfigurationProperties = RevocationListConfigurationProperties(),
)

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
) {
    //eager evaluation → fail on load
    val lifeTime: Duration = Duration.parse(lifetime)
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
     * Valid API keys for revocation endpoints
     */
    val apiKeys: Collection<ApiKeyConfigurationProperties> = listOf(),
    /**
     * Configuration for nonces used during device binding
     */
    val deviceBinding: DeviceBindingConfigurationProperties = DeviceBindingConfigurationProperties(),
)

data class DeviceBindingConfigurationProperties(
    val attestation: AttestationConfigurationProperties = AttestationConfigurationProperties(),
)

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

enum class KeyType {
    FILE,
    MEMORY,
    KEYSTORE,
    HSMFACADE,
}

