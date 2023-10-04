package at.asitplus.wallet.backend.controller

import at.asitplus.wallet.backend.service.ChallengeService
import io.matthewnelson.component.base64.encodeBase64
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.HttpServletRequest

/**
 * Delivers an authn challenge to be used by clients for signing
 * a response to be used for authentication in [BindingController].
 */
@RestController
class AuthnController(
    private val deviceBindingAuthnChallengeService: ChallengeService,
) {


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
        Napier.i("/authn/devicebinding/challenge called")
        val challenge = deviceBindingAuthnChallengeService.generate().encodeToString(Base64())
        return ResponseEntity.ok(challenge)
            .also { Napier.i("/authn/devicebinding/challenge returns '$challenge'") } // This has no relation to user, possible bug?
    }

}