package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.NextMessage
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ApiController {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @Autowired
    private lateinit var issueCredentialMessenger: IssueCredentialMessenger

    @Autowired
    private lateinit var issuerAgent: Agent

    @PostMapping("/issue")
    fun issueCredential(@RequestBody body: String): ResponseEntity<String> {
        logger.info("/issue called with body: $body")
        return runBlocking {
            try {
                when (val result = issueCredentialMessenger.parseMessage(body)) {
                    is NextMessage.Finished -> {
                        logger.info("/issue returning empty body")
                        ResponseEntity.status(HttpStatus.OK).build()
                    }
                    is NextMessage.Send -> {
                        logger.info("/issue returning ${result.message}")
                        ResponseEntity.ok(result.message)
                    }
                    is NextMessage.Error -> {
                        logger.error("/issue returning 400, incorrect protocol state")
                        ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
                    }
                }
            } catch (e: Throwable) {
                // still necessary to send a correct status to callers
                logger.error("/issue returning 500, server error", e)
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            }
        }
    }


    @GetMapping("/credentials/status/1")
    fun checkRevocation(): ResponseEntity<String> {
        logger.info("/credentials/status/1 called")
        return runBlocking {
            try {
                ResponseEntity.ok(issuerAgent.issueRevocationListCredential())
            } catch (e: Throwable) {
                logger.error("/credentials/status/1 returning 500, server error", e)
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            }
        }
    }

}