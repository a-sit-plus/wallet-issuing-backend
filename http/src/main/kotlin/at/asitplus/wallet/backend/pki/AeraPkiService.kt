package at.asitplus.wallet.backend.pki

import at.asitplus.wallet.backend.Extensions.appendPath
import at.asitplus.wallet.lib.decodeBase64ToArray
import at.asitplus.wallet.lib.encodeBase64
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.toKotlinInstant
import org.bouncycastle.cert.X509CertificateHolder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate
import kotlin.time.Duration
import java.util.*

/**
 * Implements [PkiService] by calling the external PKI service from BRZ,
 * called "AERA". In essence, it sends REST requests to an external server.
 */
class AeraPkiService(
    private val certValidity: Duration,
    private val url: String,
    private val restTemplate: RestTemplate,
    private val clock: Clock
) : PkiService {


    override fun verifyAndSign(csrEncoded: ByteArray, expectedSubject: String): SignedCertificate? =
        kotlin.runCatching {
            val csr = PkiUtils.verifyCsr(csrEncoded, expectedSubject)
                ?: return null.also {
                    Napier.w("verifyAndSign: CSR not verified")
                    Napier.v("CSR: ${csrEncoded.encodeBase64()}")
                }
            val requestDto = SignRequestDto(
                csr = csr.encoded.encodeBase64(),
                expirationTimestamp = (clock.now() + certValidity).epochSeconds,
            )
            val headers = HttpHeaders().also { it.contentType = MediaType.APPLICATION_JSON }
            val requestEntity = HttpEntity(requestDto, headers)
            val url = appendPath(url, "v1", "request-cert")
            Napier.i("verifyAndSign: Posting to '$url'")
            Napier.v("Request Data: $requestEntity")
            val response =
                restTemplate.postForEntity(url, requestEntity, SignResponseDto::class.java)
            Napier.i("verifyAndSign: Got response")
            Napier.v("Response: $response")
            val encoded = response.body?.certificate?.decodeBase64ToArray()
                ?: return null
            val validUntil = X509CertificateHolder(encoded).notAfter.toInstant().toKotlinInstant()
            SignedCertificate(encoded, validUntil)
        }.getOrElse {
            Napier.e("verifyAndSign: error", it) // TODO I think bouncycastle exceptions are fine?
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
        Napier.i("revokeCertificate: Posting to '$url'")
        Napier.v("Request Data: $requestDto")
        val response = restTemplate.postForEntity(url, requestEntity, RevokeResponseDto::class.java)
        Napier.i("revokeCertificate: Got response")
        Napier.v("Response: $response")
        if (!response.statusCode.is2xxSuccessful || response.body?.result != true) {
            Napier.w("revokeCertificate: Not successful")
            Napier.v("Certificate: ${certificate.encodeBase64()}")
        }
    }.getOrElse {
        Napier.e("revokeCertificate got error", it) // TODO I think bouncycastle exceptions are fine?
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