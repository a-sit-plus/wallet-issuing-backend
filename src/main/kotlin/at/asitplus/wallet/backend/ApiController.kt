package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.data.Agent
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

    @GetMapping("/issue")
    fun issueCredential(@RequestParam keyId: String, @RequestParam token: String): ResponseEntity<String> {
        logger.info("/issue called with $keyId and $token")
        if (!authTokenService.validateToken(token)) {
            logger.info("token is not valid")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val vcSerialized = issuer.issueCredential(keyId)
        logger.info("returning $vcSerialized")
        return ResponseEntity.ok(vcSerialized.compactJws)
    }

}