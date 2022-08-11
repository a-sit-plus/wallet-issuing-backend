package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.config.AuthnConfigurationProperties

/**
 * Service to validate an API key from a client's request
 */
interface ApiKeyAuthnService {

    fun validate(apiKey: String): String?

}


class SimpleApiKeyAuthnService(
    private val authnConfigurationProperties: AuthnConfigurationProperties,
) : ApiKeyAuthnService {

    override fun validate(apiKey: String): String? {
        return authnConfigurationProperties.apiKeys.firstOrNull { it.key == apiKey }?.name
    }

}