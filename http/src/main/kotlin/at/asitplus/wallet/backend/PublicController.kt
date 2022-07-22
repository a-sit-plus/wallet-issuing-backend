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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

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
        summary = "Get currently valid VC revocation lists",
        description = "Get a JSON array of endpoints serving 'Revocation List 2020'-formatted revocation lists",
        responses = [
            ApiResponse(
                description = "A JSON array with a list of endpoints",
                content = [Content(examples = [ExampleObject(value = "[\"https://wallet.a-sit.at/credentials/status/2022\", " +
                        "\"https://wallet.a-sit.at/credentials/status/2022\"]")])]
            ),
            ApiResponse(responseCode = "500", ref = "errorResponse"),
        ]
    )
    @GetMapping("/credentials/status/current")
    fun getCurrentVcRevocationLists() = runBlocking {
        log.info("/credentials/status/current called")
        val rl = issuer.compileCurrentRevocationLists()
        log.info("/credentials/status/current returns {}", rl)
        ResponseEntity.ok(rl)
    }

    @Operation(
        summary = "Get the VC revocation list",
        description = "Get a list of revoked credentials in 'Revocation List 2020' format",
        responses = [
            ApiResponse(
                description = "A verifiable credential in 'Revocation List 2020' format",
                content = [Content(examples = [ExampleObject(value = "<JWS containing RevocationList2020 payload>")])]
            ),
            ApiResponse(responseCode = "500", ref = "errorResponse"),
        ]
    )
    @GetMapping("/credentials/status/{timePeriod}")
    fun getVcRevocationList(@PathVariable timePeriod:Int) = runBlocking {
        log.info("/credentials/status/$timePeriod called")
        val rl = issuer.issueRevocationListCredential(timePeriod)
        log.info("/credentials/status/$timePeriod returns {}", rl)
        ResponseEntity.ok(rl)
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
            ApiResponse(responseCode = "500", ref = "errorResponse"),
        ]
    )
    @GetMapping("/crl/1")
    fun getCertificateRevocationList(): ResponseEntity<ByteArray> {
        log.info("/crl/1 called")
        val crl = pkiService.getCrl()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                .also { log.warn("/crl/1 returns 404, not found") }
        log.info("/crl/1 returns {}", crl.encodeBase64())
        return ResponseEntity.ok(crl)
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
            ApiResponse(responseCode = "500", ref = "errorResponse"),
        ]
    )
    @GetMapping("/ca/1")
    fun getCaCertificate(): ResponseEntity<ByteArray> {
        log.info("/ca/1 called")
        val caCertificate = pkiService.getCaCertificate()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                .also { log.warn("/ca/1 returns 404, not found") }
        log.info("/ca/1 returns {}", caCertificate.encodeBase64())
        return ResponseEntity.ok(caCertificate)
    }

}