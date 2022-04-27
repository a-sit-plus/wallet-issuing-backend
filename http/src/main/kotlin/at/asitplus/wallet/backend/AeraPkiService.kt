package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.PkiUtils.appendPath
import at.asitplus.wallet.lib.decodeBase64ToArray
import at.asitplus.wallet.lib.encodeBase64
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.UUID

class AeraPkiService(
    private val certValidityDays: Int,
    private val url: String,
    private val restTemplate: RestTemplate,
) : PkiService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun verifyAndSign(csrEncoded: ByteArray, expectedSubject: String): ByteArray? {
        try {
            val csr = PkiUtils.verifyCsr(csrEncoded, expectedSubject) ?: return null
            val requestDto = SignRequestDto(
                csr = csr.encoded.encodeBase64(),
                expirationTimestamp = Instant.now().plusSeconds(certValidityDays * 24L * 60L * 60L).epochSecond,
            )
            val headers = HttpHeaders().also { it.contentType = MediaType.APPLICATION_JSON }
            val requestEntity = HttpEntity(requestDto, headers)
            val url = appendPath(url, "v1", "request-cert")
            log.info("Posting to '{}' with {}", url, requestEntity)
            val response = restTemplate.postForEntity(url, requestEntity, SignResponseDto::class.java)
            log.info("Got response {}", response)
            return response.body?.certificate?.decodeBase64ToArray()
        } catch (e: Throwable) {
            log.warn("verifyAndSign: error", e)
            return null
        }
    }

    override fun buildCrl(): ByteArray {
        // TODO get CRL from AERA?
        return byteArrayOf()
    }

    override fun revokeCertificate(certificate: ByteArray) {
        val requestDto = RevokeRequestDto(certificate = certificate.encodeBase64())
        val headers = HttpHeaders().also { it.contentType = MediaType.APPLICATION_JSON }
        val requestEntity = HttpEntity(requestDto, headers)
        val url = appendPath(url, "v1", "revoke-certificate")
        log.info("Posting to '{}' with {}", url, requestDto)
        val response = restTemplate.postForEntity(url, requestEntity, RevokeResponseDto::class.java)
        log.info("Got response {}", response)
        if (!response.statusCode.is2xxSuccessful || response.body?.result != true)
            log.warn("Revocation call was not successful")
    }

    data class RevokeRequestDto(
        val certificate: String,
        val transactionID: String = UUID.randomUUID().toString(),
    )

    data class RevokeResponseDto(
        val description: String,
        val result: Boolean,
    )

    data class SignRequestDto(
        val csr: String,
        val expirationTimestamp: Long,
        val transactionID: String = UUID.randomUUID().toString(),
    )

    data class SignResponseDto(
        val description: String,
        val result: Boolean,
        val certificate: String,
    )

}