package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.IssuerAgent
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
 * - Revocation list for Verifiable Credentials
 */
@RestController
class PublicController(
    private val issuerAgent: IssuerAgent,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Operation(
        summary = "Get the revocation list",
        description = "Get a list of revoked credentials in 'Revocation List 2020' format",
        responses = [ApiResponse(
            description = "IssueCredential message of the IssueCredential protocol between Wallet and Issuer",
            content = [Content(examples = [ExampleObject(value = "<JWS containing RevocationList2020 payload>")])]
        )]
    )
    @GetMapping("/credentials/status/1")
    fun checkRevocation() = runBlocking {
        log.info("/credentials/status/1 called")
        try {
            ResponseEntity.ok(issuerAgent.issueRevocationListCredential())
        } catch (e: Throwable) {
            log.error("/credentials/status/1 returning 500, server error", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

}