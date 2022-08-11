package at.asitplus.wallet.backend.controller

import at.asitplus.wallet.backend.service.ChallengeService
import at.asitplus.wallet.lib.encodeBase64
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

/**
 * Delivers an authn challenge to be used by clients for signing
 * a response to be used for authentication in [BindingController].
 */
@RestController
class AuthnController(
    private val deviceBindingAuthnChallengeService: ChallengeService,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Operation(
        summary = "Get a challenge",
        description = "Returns a fresh challenge to be used by the client in `deviceBinding` authentication." +
                "This is the same as the client would get on an unauthenticated request in the `WWW-Authenticate` header.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Challenge in Base64 encoding",
                content = [Content(examples = [ExampleObject(value = "OBU7Uz4vI2uRmeZtGzm5FbNmVNpwNnwWQ06P15fRpiI=")])]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
        ]
    )
    @GetMapping("/authn/devicebinding/challenge")
    fun deviceBindingAuthnChallenge(
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        log.info("/authn/devicebinding/challenge called")
        val challenge = deviceBindingAuthnChallengeService.generate().encodeBase64()
        return ResponseEntity.ok(challenge)
            .also { log.info("/authn/devicebinding/challenge returns '{}'", challenge) }
    }

}