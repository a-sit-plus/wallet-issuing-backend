package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.Extensions.InstantNowPlusDays
import at.asitplus.wallet.backend.Extensions.appendPath
import at.asitplus.wallet.lib.decodeBase64ToArray
import at.asitplus.wallet.lib.encodeBase64
import org.bouncycastle.cert.X509CertificateHolder
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate
import java.util.UUID

class AeraPkiService(
    private val certValidityDays: Int,
    private val url: String,
    private val restTemplate: RestTemplate,
) : PkiService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun verifyAndSign(csrEncoded: ByteArray, expectedSubject: String): SignedCertificate? =
        kotlin.runCatching {
            val csr = PkiUtils.verifyCsr(csrEncoded, expectedSubject)
                ?: return null.also { log.warn("verifyAndSign: CSR not verified: {}", csrEncoded.encodeBase64()) }
            val requestDto = SignRequestDto(
                csr = csr.encoded.encodeBase64(),
                expirationTimestamp = InstantNowPlusDays(certValidityDays).epochSecond,
            )
            val headers = HttpHeaders().also { it.contentType = MediaType.APPLICATION_JSON }
            val requestEntity = HttpEntity(requestDto, headers)
            val url = appendPath(url, "v1", "request-cert")
            log.info("verifyAndSign: Posting to '{}' with {}", url, requestEntity)
            val response = restTemplate.postForEntity(url, requestEntity, SignResponseDto::class.java)
            log.info("verifyAndSign: Got response {}", response)
            val encoded = response.body?.certificate?.decodeBase64ToArray()
                ?: return null
            val validUntil = X509CertificateHolder(encoded).notAfter.toInstant()
            SignedCertificate(encoded, validUntil)
        }.getOrElse {
            log.error("verifyAndSign: error", it)
            null
        }

    /**
     * CRL from AERA is hosted at an external URL
     */
    override fun getCrl(): ByteArray? {
        return null
    }

    /**
     * CA from AERA is available externally
     */
    override fun getCaCertificate(): ByteArray? {
        return null
    }

    override fun revokeCertificate(certificate: ByteArray) = kotlin.runCatching {
        val requestDto = RevokeRequestDto(certificate = certificate.encodeBase64())
        val headers = HttpHeaders().also { it.contentType = MediaType.APPLICATION_JSON }
        val requestEntity = HttpEntity(requestDto, headers)
        val url = appendPath(url, "v1", "revoke-certificate")
        log.info("revokeCertificate: Posting to '{}' with {}", url, requestDto)
        val response = restTemplate.postForEntity(url, requestEntity, RevokeResponseDto::class.java)
        log.info("revokeCertificate: Got response {}", response)
        if (!response.statusCode.is2xxSuccessful || response.body?.result != true)
            log.warn("revokeCertificate: Not successful for '{}'", certificate.encodeBase64())
    }.getOrElse {
        log.error("revokeCertificate got error", it)
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