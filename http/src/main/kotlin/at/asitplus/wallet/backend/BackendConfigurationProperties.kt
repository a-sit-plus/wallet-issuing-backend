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
    val publicContext: String,
    /**
     * Lifetime of the credentials issued, e.g. 60 minutes or 6 months
     */
    val credentialLifetime: Duration = Duration.ofMinutes(60),
    val randomPhotoLocation: URI = URI.create("classpath:photos"),
    val issuerKey: KeyConfiguration = KeyConfiguration(),
    val debug: DebugConfigurationProperties = DebugConfigurationProperties(),
    val authn: AuthnConfigurationProperties = AuthnConfigurationProperties(),
)

@ConstructorBinding
data class DebugConfigurationProperties(
    val enabled: Boolean = false,
    val qrCodeSize: Int = 400,
)

@ConstructorBinding
data class AuthnConfigurationProperties(
    val challengeTimeoutSeconds: Int = 60,
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