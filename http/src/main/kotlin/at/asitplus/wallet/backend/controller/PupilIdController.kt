package at.asitplus.wallet.backend.controller

import at.asitplus.wallet.backend.ProfileConstants
import at.asitplus.wallet.backend.auth.WebSecurityConstants.AUTHORITY_DEVICE_BINDING
import at.asitplus.wallet.backend.auth.WebSecurityConstants.AUTHORITY_OIDC
import at.asitplus.wallet.backend.service.IssueCredentialAdapter
import at.asitplus.wallet.lib.aries.NextMessage
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
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.HttpServletRequest

/**
 * Provides endpoints in the PupilId deployment:
 * - REST for Wallet App to get credentials (with a device binding)
 */
@Profile(ProfileConstants.PUPILID)
@RestController
class PupilIdController(
    private val issueCredentialAdapter: IssueCredentialAdapter,
) {
    @Operation(
        summary = "Retrieve consent text to using PupilIds",
        description = "Shows a consent the user has to accept to continue using PupilIds.",
        security = [SecurityRequirement(name = "oidcPupil")],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Text to show the user to consent using PupilIds",
                content = [Content(examples = [ExampleObject(value = "Die gesetzliche Grundlage ...")])]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Client is not authenticated, i.e. needs to authenticate with OIDC first",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(responseCode = "500", ref = "errorResponse"),
        ]
    )
    @GetMapping("/pupilid/consent/retrieve")
    @PreAuthorize("hasAuthority(\"$AUTHORITY_OIDC\")")
    fun consent(
        authentication: Authentication,
    ): ResponseEntity<String> {
        Napier.i("/pupilid/consent/retrieve called")
        Napier.v("/pupilid/consent/retrieve called for $authentication")

        val principal = authentication.principal
        if (principal !is OAuth2AuthenticatedPrincipal) {
            Napier.e("/pupilid/consent/retrieve returns unauthorized")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        Napier.i(principal.attributes.toString())
        val bpk = principal.getAttribute<String>("sub")
        if (bpk == null) {
            Napier.e("/pupilid/consent/retrieve returns error, no subject")
            Napier.v("/pupilid/consent/retrieve returns error, no subject for $principal")
            return ResponseEntity.internalServerError().build()
        }
        // TODO Call external service to retrieve stored consent, if any
        // TODO Think about data structure going to the client
        return ResponseEntity.ok("Please consent ...")
    }

    @Operation(
        summary = "Store consent to using PupilIds",
        description = "Shows a consent the user has to accept to continue using PupilIds.",
        security = [SecurityRequirement(name = "oidcPupil")],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Text to show the user to consent using PupilIds",
                content = [Content(examples = [ExampleObject(value = "Die gesetzliche Grundlage ...")])]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Client is not authenticated, i.e. needs to authenticate with OIDC first",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(responseCode = "500", ref = "errorResponse"),
        ]
    )
    @PostMapping("/pupilid/consent/confirm")
    @PreAuthorize("hasAuthority(\"$AUTHORITY_OIDC\")")
    fun storeConsent(
        authentication: Authentication,
    ): ResponseEntity<String> {
        Napier.i("/pupilid/consent/confirm called")
        Napier.v("/pupilid/consent/confirm called for $authentication")
        // TODO store the consent at external service
        return ResponseEntity.ok("Consent first")
    }

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
        }.also { request.logout() }
    }

}