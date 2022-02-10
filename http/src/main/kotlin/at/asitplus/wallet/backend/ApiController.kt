package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.agent.DelegatingProtocolMessenger
import at.asitplus.wallet.lib.agent.NextMessage
import io.swagger.v3.oas.annotations.Operation
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ApiController(
    private val delegatingProtocolMessenger: DelegatingProtocolMessenger,
    private val issuerAgent: Agent,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @PostMapping("/issue")
    fun issueCredential(@RequestBody body: String) = runBlocking {
        log.info("/issue called with body: $body")
        try {
            when (val result = delegatingProtocolMessenger.parseMessage(body)) {
                is NextMessage.Finished -> {
                    log.info("/issue returning empty body")
                    ResponseEntity.status(HttpStatus.OK).build()
                }
                is NextMessage.Send -> {
                    log.info("/issue returning ${result.message}")
                    ResponseEntity.ok(result.message)
                }
                is NextMessage.Error -> {
                    log.error("/issue returning 400, incorrect protocol state")
                    ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
                }
                is NextMessage.SendProblemReport -> {
                    log.info("/issue returning problem report ${result.message}")
                    ResponseEntity.ok(result.message)
                }
                is NextMessage.ReceivedProblemReport -> {
                    log.info("/issue received a problem report ${result.message}")
                    ResponseEntity.ok().build()
                }
            }
        } catch (e: Throwable) {
            // still necessary to send a correct status to callers
            log.error("/issue returning 500, server error", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

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