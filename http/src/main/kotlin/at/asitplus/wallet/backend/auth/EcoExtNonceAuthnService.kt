package at.asitplus.wallet.backend.auth

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import org.springframework.web.client.getForEntity
import java.net.URI

/**
 * Validates the ext. nonce provided by a Wallet App ([ExtNonceAuthnToken]),
 * by calling the external webservice defined in the configuration.
 * Assumption is, that the user has scanned a QR code containing a nonce
 * from the external service, that will be provided here by the Wallet App.
 */
class EcoExtNonceAuthnService(
    private val url: URI,
    private val restTemplate: RestTemplate
) : ExtNonceAuthnService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun generateNonce(): ExtNonceAuthnService.NonceBpk? {
        return null
    }

    override fun exchangeNonceForBpk(nonce: String): String? = kotlin.runCatching {
        val entity = restTemplate.getForEntity<CardCreationCodeResolveResult>(
            "$url/CardCreationCode/{cardCode}",
            uriVariables = mapOf("cardCode" to nonce)
        ).also { log.debug("exchangeNonceForBpk('{}') got {}", nonce, it) }
        entity.body?.bpk
    }.getOrElse {
        log.error("exchangeNonceForBpk('{}') got error", nonce, it)
        null
    }

    override fun invalidateNonce(nonce: String): Boolean = kotlin.runCatching {
        val entity = restTemplate.exchange<Any>(
            "$url/CardCreationCode/{cardCode}",
            HttpMethod.DELETE,
            uriVariables = mapOf("cardCode" to nonce)
        ).also { log.debug("invalidateNonce('{}') got {}", nonce, it) }
        entity.statusCode.is2xxSuccessful
    }.getOrDefault(false)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CardCreationCodeResolveResult(
        val bpk: String,
    )

}