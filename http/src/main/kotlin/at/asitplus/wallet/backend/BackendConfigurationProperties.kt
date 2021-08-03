package at.asitplus.wallet.backend

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties(prefix = "backend")
@ConstructorBinding
data class BackendConfigurationProperties(
    val publicContext: String
)