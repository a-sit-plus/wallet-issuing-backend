package at.asitplus.wallet.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import kotlin.time.Duration

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
     * Configure details about revocation lists for Verifiable Credentials
     */
    val revocationList: RevocationListConfigurationProperties = RevocationListConfigurationProperties(),
    /**
     * Configure external service for getting OTT for EPrescription credentials
     */
    val eprescription: EPrescriptionConfigurationProperties = EPrescriptionConfigurationProperties()
)

data class EPrescriptionConfigurationProperties(
    /**
     * URL of the external service
     */
    val url: String = "https://example.com/",
    /**
     * Will be sent as header `X-API-Key` to the service at [url]
     */
    val apiKey: String = "",
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
    val cwtPath = path.apply { if (endsWith("/")) this else "$this/" } + "cwt/"
    val jwtPath = path.apply { if (endsWith("/")) this else "$this/" } + "jwt/"
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

