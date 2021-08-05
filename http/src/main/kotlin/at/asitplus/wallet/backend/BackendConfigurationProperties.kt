package at.asitplus.wallet.backend

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.net.URI
import java.time.Duration

@ConfigurationProperties(prefix = "backend")
@ConstructorBinding
data class BackendConfigurationProperties(
    val publicContext: String,
    val credentialLifetime: Duration = Duration.ofMinutes(60),
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