package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.Agent
import io.swagger.v3.oas.annotations.Operation
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PublicController(
    private val issuerAgent: Agent,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Operation(
        summary = "Get a the revocation list",
        description = "Get a list revoked credentials in 'Revocation List 2020' format"
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