package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.encodeBase64
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public endpoints, available without authentication:
 * - Revocation list for Verifiable Credentials (RevocationList2020)
 * - Revocation list for Binding Certificates (PKI CRL)
 * - PKI CA certificate
 */
@RestController
class PublicController(
    private val issuer: Issuer,
    private val pkiService: PkiService,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Operation(
        summary = "Get the VC revocation list",
        description = "Get a list of revoked credentials in 'Revocation List 2020' format",
        responses = [
            ApiResponse(
                description = "A verifiable credential in 'Revocation List 2020' format",
                content = [Content(examples = [ExampleObject(value = "<JWS containing RevocationList2020 payload>")])]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
        ]
    )
    @GetMapping("/credentials/status/1")
    fun getVcRevocationList() = runBlocking {
        log.info("/credentials/status/1 called")
        try {
            val rl = issuerAgent.issueRevocationListCredential()
            log.info("/credentials/status/1 returns {}", rl)
            ResponseEntity.ok(rl)
        } catch (e: Throwable) {
            log.error("/credentials/status/1 returning 500, server error", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @Operation(
        summary = "Get the X.509 revocation list",
        description = "Get a list of revoked certificates in X.509 CRL format, if the internal PKI is used",
        responses = [
            ApiResponse(
                description = "Binary encoded X.509 CRL object",
                content = [Content(examples = [ExampleObject(value = "<Binary encoded X.509 CRL object>")])]
            ),
            ApiResponse(
                responseCode = "404",
                description = "CRL not found, e.g. it is hosted at an external URL",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
        ]
    )
    @GetMapping("/crl/1")
    fun getCertificateRevocationList(): ResponseEntity<ByteArray> {
        log.info("/crl/1 called")
        try {
            val crl = pkiService.getCrl()
            if (crl != null) {
                log.info("/crl/1 returns {}", crl.encodeBase64())
                return ResponseEntity.ok(crl)
            }
            log.info("/crl/1 returns 404, not found")
            return ResponseEntity.notFound().build()
        } catch (e: Throwable) {
            log.error("/crl/1 returning 500, server error", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @Operation(
        summary = "Get the CA certificate",
        description = "Get the certificate for the key pair that signs device binding certificates",
        responses = [
            ApiResponse(
                description = "Binary encoded X.509 Certificate object",
                content = [Content(examples = [ExampleObject(value = "<Binary encoded X.509 Certificate object>")])]
            ),
            ApiResponse(
                responseCode = "404",
                description = "CA certificate not found, e.g. it is hosted at an external URL",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
        ]
    )
    @GetMapping("/ca/1")
    fun getCaCertificate(): ResponseEntity<ByteArray> {
        log.info("/ca/1 called")
        try {
            val caCertificate = pkiService.getCaCertificate()
            if (caCertificate != null) {
                log.info("/ca/1 returns {}", caCertificate.encodeBase64())
                return ResponseEntity.ok(caCertificate)
            }
            log.info("/ca/1 returns 404, not found")
            return ResponseEntity.notFound().build()
        } catch (e: Throwable) {
            log.error("/ca/1 returning 500, server error", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

}