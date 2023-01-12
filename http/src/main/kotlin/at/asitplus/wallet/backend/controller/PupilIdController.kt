package at.asitplus.wallet.backend.controller

import at.asitplus.wallet.backend.auth.WebSecurityConstants.AUTHORITY_DEVICE_BINDING
import at.asitplus.wallet.backend.service.IssueCredentialAdapter
import at.asitplus.wallet.lib.agent.NextMessage
import io.github.aakira.napier.Napier
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

/**
 * Provides endpoints in the PupilId deployment:
 * - REST for Wallet App to get credentials (with a device binding)
 */
@Profile("pupilid")
@RestController
class PupilIdController(
    private val issueCredentialAdapter: IssueCredentialAdapter,
) {


    @Operation(
        summary = "Issue credentials",
        description = "Issues a fresh instance of a PupilId to the Wallet app.",
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
            ApiResponse(responseCode = "500", ref = "errorResponse"),
        ]
    )
    @PostMapping("/pupilid/issue")
    @PreAuthorize("hasAuthority(\"$AUTHORITY_DEVICE_BINDING\")")
    fun issueCredential(
        @RequestBody body: String,
        authentication: Authentication,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        Napier.i("/pupilid/issue called")
        Napier.v("/pupilid/issue called for $authentication with '$body'")
        return when (val result = issueCredentialAdapter.parseMessage(body)) {
            is NextMessage.Result<*> -> ResponseEntity.ok().build<String>()
                .also { Napier.i("/pupilid/issue returns HTTP 200: Finished") }

            is NextMessage.Send -> ResponseEntity.ok(result.message)
                .also {
                    Napier.i("/pupilid/issue returns HTTP 200")
                    Napier.v("/pupilid/issue returns HTTP 200: ${result.message.take(128)}...")
                }

            is NextMessage.Error -> ResponseEntity.status(HttpStatus.BAD_REQUEST).build<String>()
                .also { Napier.w("/pupilid/issue returns HTTP 400: Incorrect protocol state") }

            is NextMessage.SendProblemReport -> ResponseEntity.ok(result.message)
                .also {
                    Napier.i("/pupilid/issue returns HTTP 200: Problem Report")
                    Napier.v("Problem Report ${result.message}")
                }

            is NextMessage.ReceivedProblemReport -> ResponseEntity.ok().build<String>()
                .also {
                    Napier.i("/pupilid/issue returns HTTP 200: Received Problem Report")
                    Napier.v("Received Problem Report ${result.message}")
                }

            else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build<String>()
                .also {
                    Napier.w("/pupilid/issue returns HTTP 500: Internal error")
                    Napier.v("Internal error $result")
                }
        }.also { request.logout() }
    }

}