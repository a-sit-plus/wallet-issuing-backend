package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.data.Agent
import at.asitplus.wallet.backend.model.IdentifierRegistry
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ApiController {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @Autowired
    lateinit var issuer: Agent

    @Autowired
    lateinit var authTokenService: AuthTokenService

    @Autowired
    lateinit var identifierRegistry: IdentifierRegistry

    @GetMapping("/issue")
    fun issueCredential(@RequestParam keyId: String, @RequestParam token: String): ResponseEntity<String> {
        logger.info("/issue called with $keyId and $token")
        if (!authTokenService.validateToken(token)) {
            logger.info("token is not valid")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val vcSerialized = issuer.issueCredential(keyId)
        logger.info("returning $vcSerialized")
        identifierRegistry.addIdenitfier(keyId);
        return ResponseEntity.ok(vcSerialized.compactJws)
    }

    @GetMapping("/check")
    fun checkRevocation(@RequestParam keyId: String): ResponseEntity<String> {
        logger.info("/check called with $keyId")
        // TODO check what we decided over revocation
        val revoked = identifierRegistry.isRevoked(keyId);
        return ResponseEntity.ok(revoked.toString())
    }

    @GetMapping("/revoke") // TODO do we need a signed revocation request
    fun revoke(@RequestParam keyId: String): ResponseEntity<String> {
        logger.info("/revoke called with $keyId")
        val revoked = identifierRegistry.revoke(keyId);
        return ResponseEntity.ok(revoked.toString())
    }

}