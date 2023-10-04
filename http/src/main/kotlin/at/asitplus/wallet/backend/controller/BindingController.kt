package at.asitplus.wallet.backend.controller

import at.asitplus.wallet.backend.auth.DeviceBindingAuthnToken
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.backend.auth.ExtNonceAuthnToken
import at.asitplus.wallet.backend.auth.WebSecurityConstants.AUTHORITY_OIDC
import at.asitplus.wallet.backend.auth.WebSecurityConstants.AUTHORITY_PUPIL
import at.asitplus.wallet.backend.auth.WebSecurityConstants.X_AUTH_EXT_NONCE
import at.asitplus.wallet.backend.auth.WebSecurityConstants.X_AUTH_TOKEN
import at.asitplus.wallet.backend.data.Rfc7807Problem
import at.asitplus.wallet.backend.service.BindingService
import at.asitplus.wallet.pupilid.*
import io.github.aakira.napier.Napier
import io.ktor.http.*
import io.ktor.util.date.*
import io.matthewnelson.component.base64.decodeBase64ToArray
import io.matthewnelson.component.base64.encodeBase64
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArrayOrNull
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import kotlinx.datetime.Clock
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import jakarta.servlet.http.HttpSession
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val SESSION_ATTR_CERTIFICATE = "certificate"

@RestController
class BindingController(
    private val extNonceAuthnService: ExtNonceAuthnService,
    private val bindingService: BindingService,
) {

    @Value("\${backend.max-drift:PT12H}")
    private lateinit var timeDrift: String
    private val timeDifference: Duration by lazy { Duration.parse(timeDrift) }

    @Operation(
        summary = "Initiate binding",
        description = "Get parameters to initiate a binding between a key on the mobile device and the authenticated user.",
        security = [SecurityRequirement(name = "extNonce")],
        responses = [
            ApiResponse(responseCode = "200", description = "Binding parameters have been created"),
            ApiResponse(
                responseCode = "403",
                description = "Client is not authenticated, i.e. it needs to send ext. nonce in header `$X_AUTH_EXT_NONCE`",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(responseCode = "500", ref = "errorResponse"),
        ],
    )
    @PostMapping("/binding/start")
    @PreAuthorize("hasAnyAuthority(\"$AUTHORITY_PUPIL\", \"$AUTHORITY_OIDC\")")
    fun requestBindingParams(
        @RequestBody body: BindingParamsRequestJ,
        @RequestHeader(name = HttpHeaders.DATE, required = false) dateHeader: String?,
        principal: Principal
    ): ResponseEntity<out Any> {
        Napier.apply {
            i("/binding/start called")
            v("/binding/start called with client date $dateHeader (which may differ by at most $timeDifference)")
        }
        Napier.v("principal: $principal, body: $body")

        runCatching {
            dateHeader?.fromHttpToGmtDate()?.timestamp?.milliseconds?.let { clientTime ->

                if ((clientTime - Clock.System.now().epochSeconds.seconds) > timeDifference) {
                    Napier.w("Client clock too far in the future")
                    return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                        .contentType(MediaType("application", "problem+json"))
                        .body(
                            Rfc7807Problem(
                                type = "https://wallet.a-sit.at/schemas/error/client/date",
                                title = "Client clock too far in the future",
                                status = HttpStatus.PRECONDITION_FAILED.value(),
                                detail = Clock.System.now().epochSeconds.toString()
                            ).toString()
                        )
                } else {
                    Napier.v("Client clock is fine: $clientTime")
                }

            }
        }.getOrElse {
            Napier.w { "/binding/start could not parse client date header $dateHeader" }
        }

        val response = bindingService.getBindingParams(body.deviceName)
        return ResponseEntity.ok(BindingParamsResponseJ(response.challenge, response.subject, response.keyType))
            .also { Napier.i("/binding/start returns HTTP 200: $it") /* this is fine, no personal data */ }
    }

    @Operation(
        summary = "Create binding",
        description = "Post certification request to get a binding.",
        security = [SecurityRequirement(name = "xAuthToken")],
        responses = [
            ApiResponse(responseCode = "200", description = "Binding has been created"),
            ApiResponse(
                responseCode = "400",
                description = "Value for `challenge` or `csr` is not valid",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(
                responseCode = "403",
                description = "Client is not authenticated, i.e. it needs to send sessionId in header `$X_AUTH_TOKEN`",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(responseCode = "500", ref = "errorResponse"),
        ],
    )
    @PostMapping("/binding/create")
    @PreAuthorize("hasAnyAuthority(\"$AUTHORITY_PUPIL\", \"$AUTHORITY_OIDC\")")
    fun postBindingCsr(
        @RequestBody body: BindingCsrRequestJ,
        principal: Principal,
        session: HttpSession,
    ): ResponseEntity<BindingCsrResponseJ> {
        Napier.i("/binding/create called")
        Napier.v("principal: $principal, body: $body")
        val response = bindingService.signCertificate(
            body.csr,
            body.challenge,
            body.deviceName,
            body.attestationCerts,
            principal.name
        ) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST)
            .also { Napier.w("/binding/create returns HTTP 400") }
        session.setAttribute(SESSION_ATTR_CERTIFICATE, response.certificate.encodeToString(Base64()))
        return ResponseEntity.ok(BindingCsrResponseJ(response.certificate, response.attestedPublicKey))
            .also {
                Napier.i("/binding/create returns HTTP 200")
                Napier.v("Returns $it")
            }
    }

    @Operation(
        summary = "Confirm binding",
        description = "Confirm the reception of the binding certificate.",
        security = [SecurityRequirement(name = "xAuthToken")],
        responses = [
            ApiResponse(responseCode = "200", description = "Confirmation has been recorded"),
            ApiResponse(
                responseCode = "400",
                description = "Value for `success` is not valid, i.e. should be `true`",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(
                responseCode = "403",
                description = "Client is not authenticated, i.e. it needs to send sessionId in header `$X_AUTH_TOKEN`",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(responseCode = "500", ref = "errorResponse"),
        ],
    )
    @PostMapping("/binding/confirm")
    @PreAuthorize("hasAnyAuthority(\"$AUTHORITY_PUPIL\", \"$AUTHORITY_OIDC\")")
    fun confirmBinding(
        @RequestBody body: BindingConfirmRequestJ,
        principal: Principal,
        session: HttpSession,
    ): ResponseEntity<BindingConfirmResponseJ> {
        Napier.i("/binding/confirm called")
        Napier.v("principal: $principal, body: $body")
        val confirmed = bindingService.confirm(body.success)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "success not set")
                .also { Napier.w("/binding/confirm returns HTTP 400: success not set") }

        // We want the client to not need to authenticate again when using the PupilIdController,
        // so we'll set the expected authentication token into the current security context
        // and do not log out the client (previously, "request.logout()" has been called here)
        if (principal is ExtNonceAuthnToken) {
            Napier.d("Setting current authentication to DeviceBindingAuthnToken")
            val certificate = session.getAttribute(SESSION_ATTR_CERTIFICATE).toString()
                .decodeToByteArrayOrNull(Base64())!!
            extNonceAuthnService.invalidateNonce(principal.credentials.toString())
            SecurityContextHolder.getContext().authentication =
                DeviceBindingAuthnToken(principal.principal.toString(), certificate)
        }
        if (principal is OAuth2AuthenticationToken) {
            val oidcUser = principal.principal as OidcUser
            Napier.d("Setting current authentication to DeviceBindingAuthnToken")
            val certificate = session.getAttribute(SESSION_ATTR_CERTIFICATE).toString()
                .decodeToByteArrayOrNull(Base64())!!
            extNonceAuthnService.invalidateNonce(principal.credentials.toString())
            SecurityContextHolder.getContext().authentication =
                DeviceBindingAuthnToken(oidcUser.subject, certificate, oidcUser.idToken)
        }
        return ResponseEntity.ok(BindingConfirmResponseJ(confirmed))
            .also { Napier.i("/binding/confirm returns HTTP 200: $it") }
    }

}
