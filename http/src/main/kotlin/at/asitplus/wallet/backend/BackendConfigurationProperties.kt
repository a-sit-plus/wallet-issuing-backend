package at.asitplus.wallet.backend

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.net.URI
import java.time.Duration

@ConfigurationProperties(prefix = "backend")
@ConstructorBinding
data class BackendConfigurationProperties(
    /**
     * Public URL of this instance, used for several URLs in messages sent to the Wallet
     */
    val publicContext: String = "http://localhost:8080/",
    /**
     * Lifetime of the credentials issued, e.g. 60 minutes or 6 months
     */
    val credentialLifetime: Duration = Duration.ofMinutes(60),
    /**
     * Key used for signing issued credentials
     * TODO Connection to HSM Facade and AERA
     */
    val issuerKey: KeyConfiguration = KeyConfiguration(),
    /**
     * Configure debug endpoints
     */
    val debug: DebugConfigurationProperties = DebugConfigurationProperties(),
    /**
     * Configure authentication of external services at this service
     */
    val authn: AuthnConfigurationProperties = AuthnConfigurationProperties(),
)

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
    /**
     * Whether to use random data for issued credentials
     */
    val randomData: Boolean = false,
    /**
     * Location of random photos to be used for issued credentials
     */
    val randomPhotoLocation: URI = URI.create("file:photos/"),
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
)

@ConstructorBinding
data class ApiKeyConfigurationProperties(
    val name: String,
    val key: String,
)

@ConstructorBinding
data class KeyConfiguration(
    val type: KeyType = KeyType.MEMORY,
    val file: KeyFileConfiguration? = null,
)

@ConstructorBinding
data class KeyFileConfiguration(
    val privateKey: URI,
    val publicKey: URI,
)

enum class KeyType {
    FILE,
    MEMORY
}