package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.NextMessageFinished
import at.asitplus.wallet.lib.agent.NextMessageToSend
import at.asitplus.wallet.lib.agent.NextMessageToSendAndWrap
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
                    is NextMessageFinished -> {
                        logger.info("/issue returning empty body")
                        ResponseEntity.status(HttpStatus.OK).build()
                    }
                    is NextMessageToSend -> {
                        logger.info("/issue returning ${result.message}")
                        ResponseEntity.ok(result.message)
                    }
                    is NextMessageToSendAndWrap -> TODO()
                }
            } catch (e: Throwable) {
                logger.info("/issue returning 400, can't process request")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
            }

        }

    }

    @GetMapping("/credentials/status/1")
    fun checkRevocation(): ResponseEntity<String> {
        return runBlocking {
            logger.info("/credentials/status/1 called")
            ResponseEntity.ok(issuerAgent.issueRevocationListCredential())
        }
    }

}