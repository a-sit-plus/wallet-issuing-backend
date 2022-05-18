package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.DeviceBindingAuthnToken
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.backend.auth.ExtNonceAuthnToken
import at.asitplus.wallet.lib.decodeBase64ToArray
import at.asitplus.wallet.lib.encodeBase64
import at.asitplus.wallet.pupilid.BindingConfirmRequestJ
import at.asitplus.wallet.pupilid.BindingConfirmResponseJ
import at.asitplus.wallet.pupilid.BindingCsrRequestJ
import at.asitplus.wallet.pupilid.BindingCsrResponseJ
import at.asitplus.wallet.pupilid.BindingParamsRequestJ
import at.asitplus.wallet.pupilid.BindingParamsResponseJ
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession

@RestController
class BindingController(
    private val extNonceAuthnService: ExtNonceAuthnService,
    private val bindingService: BindingService,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Operation(
        summary = "Initiate binding",
        description = "Get parameters to initiate a binding between a key on the mobile device and the authenticated user.",
        security = [SecurityRequirement(name = "extNonce")],
        responses = [
            ApiResponse(responseCode = "200", description = "Binding parameters have been created"),
            ApiResponse(
                responseCode = "403",
                description = "Client is not authenticated, i.e. it needs to send ext. nonce in header `X-Auth-ExtNonce`",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(responseCode = "500", ref = "errorResponse"),
        ],
    )
    @PostMapping("/binding/start")
    @PreAuthorize("hasAuthority(\"PUPIL\")")
    fun requestBindingParams(
        @RequestBody body: BindingParamsRequestJ,
        principal: Principal
    ): ResponseEntity<BindingParamsResponseJ> {
        log.info("/binding/start called for {} with {}", principal, body)
        val response = bindingService.getBindingParams(body.deviceName)
        return ResponseEntity.ok(BindingParamsResponseJ(response.challenge, response.subject, response.keyType))
            .also { log.info("/binding/start returns ok: {}", it) }
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
                description = "Client is not authenticated, i.e. it needs to send sessionId in header `X-Auth-Token`",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(responseCode = "500", ref = "errorResponse"),
        ],
    )
    @PostMapping("/binding/create")
    @PreAuthorize("hasAuthority(\"PUPIL\")")
    fun postBindingCsr(
        @RequestBody body: BindingCsrRequestJ,
        principal: Principal,
        session: HttpSession,
        request: HttpServletRequest,
    ): ResponseEntity<BindingCsrResponseJ> {
        log.info("/binding/create called for {} with {}", principal, body)
        val response = bindingService.signCertificate(
            body.csr,
            body.challenge,
            body.deviceName,
            body.attestationCerts,
            principal.name
        ) ?: return ResponseEntity.badRequest().build<BindingCsrResponseJ>()
            .also { log.info("/binding/create returns BAD_REQUEST 400") }
        session.setAttribute("certificate", response.certificate.encodeBase64())
        return ResponseEntity.ok(BindingCsrResponseJ(response.certificate, response.attestedPublicKey))
            .also { log.info("/binding/create returns ok: {}", it) }
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
                description = "Client is not authenticated, i.e. it needs to send sessionId in header `X-Auth-Token`",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(responseCode = "500", ref = "errorResponse"),
        ],
    )
    @PostMapping("/binding/confirm")
    @PreAuthorize("hasAuthority(\"PUPIL\")")
    fun confirmBinding(
        @RequestBody body: BindingConfirmRequestJ,
        principal: Principal,
        session: HttpSession,
        request: HttpServletRequest,
    ): ResponseEntity<BindingConfirmResponseJ> {
        log.info("/binding/confirm called for {} with {}", principal, body)
        val confirmed = bindingService.confirm(body.success)
            ?: return ResponseEntity.badRequest().build<BindingConfirmResponseJ>()
                .also { log.info("/binding/confirm returns success not set: {}", it) }

        // We want the client to not need to authenticate again when using the PupilIdController,
        // so we'll set the expected authentication token into the current security context
        // and do not log out the client (previously, "request.logout()" has been called here)
        if (principal is ExtNonceAuthnToken) {
            val certificate = session.getAttribute("certificate").toString().decodeBase64ToArray()!!
            extNonceAuthnService.invalidateNonce(principal.credentials.toString())
            SecurityContextHolder.getContext().authentication =
                DeviceBindingAuthnToken("", principal.principal.toString(), certificate)
        }
        return ResponseEntity.ok(BindingConfirmResponseJ(confirmed))
            .also { log.info("/binding/confirm returns ok: {}", it) }
    }

}
