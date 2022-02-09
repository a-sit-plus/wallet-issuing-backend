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