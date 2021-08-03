package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.model.IdentifierRegistry
import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.NextMessageError
import at.asitplus.wallet.lib.agent.NextMessageFinished
import at.asitplus.wallet.lib.agent.NextMessageToSend
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ApiController {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @Autowired
    private lateinit var revocationService: RevocationService

    @Autowired
    private lateinit var identifierRegistry: IdentifierRegistry

    @Autowired
    private lateinit var issueCredentialMessenger: IssueCredentialMessenger

    @PostMapping("/issue")
    fun issueCredential(@RequestBody body: String): ResponseEntity<String> {
        logger.info("/issue called with body: $body")
        return when (val result = issueCredentialMessenger.parseMessage(body)) {
            is NextMessageError -> {
                logger.info("/issue returning 400, can't process request")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
            }
            is NextMessageFinished -> {
                logger.info("/issue returning empty body")
                ResponseEntity.status(HttpStatus.OK).build()
            }
            is NextMessageToSend -> {
                logger.info("/issue returning ${result.message}")
                ResponseEntity.ok(result.message)
            }
        }
        // TODO store to identifierRegistry
    }

    @GetMapping("/revocationList")
    fun checkRevocation(@RequestParam keyId: String): ResponseEntity<String> {
        logger.info("/check called with $keyId")
        return ResponseEntity.ok(revocationService.getRevocationCredential())
    }

    @GetMapping("/revoke") // TODO do we need a signed revocation request
    fun revoke(@RequestParam keyId: String): ResponseEntity<String> {
        logger.info("/revoke called with $keyId")
        val revoked = identifierRegistry.revoke(keyId);
        return ResponseEntity.ok(revoked.toString())
    }

}