package at.asitplus.wallet.backend.auth

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import org.springframework.web.client.getForEntity
import org.springframework.web.client.postForEntity
import java.net.URI
import java.time.OffsetDateTime

class EcoExtNonceAuthnService(
    private val url: URI,
    private val restTemplate: RestTemplate
) : ExtNonceAuthnService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun generateNonce(): ExtNonceAuthnService.NonceBpk? {
        val entity = restTemplate.postForEntity<CardCreationCode>("$url/CardCreationCode/Create")
        log.debug("generateNonce() got {}", entity)
        if (!entity.statusCode.is2xxSuccessful || entity.body == null)
            return null
        val body: CardCreationCode = entity.body ?: return null
        return ExtNonceAuthnService.NonceBpk(body.code, "unknown")
    }

    override fun exchangeNonceForBpk(nonce: String): String? {
        val entity = restTemplate.getForEntity<CardCreationCodeResolveResult>(
            "$url/CardCreationCode/{cardCode}",
            uriVariables = mapOf("cardCode" to nonce)
        )
        log.debug("exchangeNonceForBpk('{}') got {}", nonce, entity)
        if (!entity.statusCode.is2xxSuccessful || entity.body == null)
            return null
        val body: CardCreationCodeResolveResult = entity.body ?: return null
        return body.bpk
    }

    override fun invalidateNonce(nonce: String): Boolean {
        val entity = restTemplate.exchange<Any>(
            "$url/CardCreationCode/{cardCode}",
            HttpMethod.DELETE,
            uriVariables = mapOf("cardCode" to nonce)
        )
        log.debug("invalidateNonce('{}') got {}", nonce, entity)
        if (!entity.statusCode.is2xxSuccessful)
            return false
        return true
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CardCreationCode(
        val code: String,
        @JsonAlias(*["vaildUntil", "validUntil"])
        val validUntil: OffsetDateTime,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CardCreationCodeResolveResult(
        val bpk: String,
        val codeValidUntil: OffsetDateTime,
    )

}