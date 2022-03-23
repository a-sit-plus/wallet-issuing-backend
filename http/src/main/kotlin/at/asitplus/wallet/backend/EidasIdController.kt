package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.NextMessage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
class EidasIdController(
    private val pupilIdService: PupilIdService,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Operation(
        summary = "Issue credentials",
        description = "Issues a fresh instance of an EidasId to the Wallet app.",
        security = [SecurityRequirement(name = "deviceBinding")],
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "RequestCredential message of the IssueCredential protocol between Wallet and Issuer",
            content = [Content(examples = [ExampleObject(value = "<DIDcomm signed message>")])]
        ),
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "IssueCredential message of the IssueCredential protocol between Wallet and Issuer",
                content = [Content(examples = [ExampleObject(value = "<DIDcomm signed message>")])]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Incorrect protocol state, i.e. this message was not expected on protocol level",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Client is not authenticated, i.e. needs to answer challenge from header `WWW-Authenticate` first",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
        ]
    )
    @PostMapping("/eidasid/issue")
    @PreAuthorize("hasAuthority(\"DEVICE_BINDING\")")
    fun issueCredential(
        @RequestBody body: String,
        authentication: Authentication,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        log.info("/eidasid/issue called for {} with '{}'", authentication, body)
        when (val result = pupilIdService.parseMessage(body)) {
            is NextMessage.Result<*> -> {
                return ResponseEntity.ok().build<String>()
                    .also { request.logout() }
                    .also { log.info("/eidasid/issue returns HTTP 200: Finished") }
            }
            is NextMessage.Send -> {
                return ResponseEntity.ok(result.message)
                    .also { request.logout() }
                    .also { log.info("/eidasid/issue returns HTTP 200: {}...", result.message.take(128)) }
            }
            is NextMessage.Error -> {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build<String>()
                    .also { request.logout() }
                    .also { log.warn("/eidasid/issue returns HTTP 400: Incorrect protocol state") }
            }
            is NextMessage.SendProblemReport -> {
                return ResponseEntity.ok(result.message)
                    .also { request.logout() }
                    .also { log.info("/eidasid/issue returns HTTP 200: Problem Report {}", result.message) }
            }
            is NextMessage.ReceivedProblemReport -> {
                return ResponseEntity.ok().build<String>()
                    .also { request.logout() }
                    .also { log.info("/eidasid/issue returns HTTP 200: Received Problem Report {}", result.message) }
            }
            else -> {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build<String>()
                    .also { request.logout() }
                    .also { log.warn("/eidasid/issue returns HTTP 500: Internal error {}", result) }
            }
        }
    }

}