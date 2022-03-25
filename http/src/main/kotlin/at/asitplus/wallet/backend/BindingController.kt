package at.asitplus.wallet.backend

import at.asitplus.wallet.BindingConfirmRequestJ
import at.asitplus.wallet.BindingConfirmResponseJ
import at.asitplus.wallet.BindingCsrRequestJ
import at.asitplus.wallet.BindingCsrResponseJ
import at.asitplus.wallet.BindingParamsRequestJ
import at.asitplus.wallet.BindingParamsResponseJ
import at.asitplus.wallet.backend.auth.DeviceBindingAuthnToken
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.backend.auth.ExtNonceAuthnToken
import at.asitplus.wallet.lib.encodeBase16
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


@RestController
class BindingController(
    private val challengeService: ChallengeService,
    private val certificateService: CertificateService,
    private val deviceBindingStorageService: DeviceBindingStorageService,
    private val extNonceAuthnService: ExtNonceAuthnService,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Operation(
        summary = "Initiate binding",
        description = "Get parameters to initiate a binding between a key on the mobile device and the authenticated user.",
        security = [SecurityRequirement(name = "extNonce")],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Binding parameters have been created",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Client is not authenticated, i.e. it needs to send ext. nonce in header `X-Auth-ExtNonce`",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
        ],
    )
    @PostMapping("/binding/start")
    @PreAuthorize("hasAuthority(\"PUPIL\")")
    fun requestBindingParams(
        @RequestBody body: BindingParamsRequestJ,
        principal: Principal
    ): ResponseEntity<BindingParamsResponseJ> {
        log.info("/binding/start called for {} with {}", principal, body)
        val challenge = challengeService.generate()
        val subject = "CN=${challenge.encodeBase16()}"
        return ResponseEntity.ok(BindingParamsResponseJ(challenge, subject, "EC"))
            .also { log.info("/binding/start returns ok: {}", it) }
    }

    @Operation(
        summary = "Create binding",
        description = "Post certification request to get a binding.",
        security = [SecurityRequirement(name = "xAuthToken")],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Binding has been created",
            ),
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
        ],
    )
    @PostMapping("/binding/create")
    @PreAuthorize("hasAuthority(\"PUPIL\")")
    fun postBindingCsr(
        @RequestBody body: BindingCsrRequestJ,
        principal: Principal,
        request: HttpServletRequest,
    ): ResponseEntity<BindingCsrResponseJ> {
        log.info("/binding/create called for {} with {}", principal, body)
        if (!challengeService.verifyAndRemove(body.challenge)) {
            return ResponseEntity.badRequest().build<BindingCsrResponseJ>()
                .also { log.info("/binding/create returns challenge invalid: {}", it) }
        }
        val certificate = certificateService.verifyAndSign(body.csr, "CN=${body.challenge.encodeBase16()}")
            ?: return ResponseEntity.badRequest().build<BindingCsrResponseJ>()
                .also { log.info("/binding/create returns CSR invalid: {}", it) }
        deviceBindingStorageService.store(principal.name, certificate, body.deviceName)
        val signedPublicKey = certificateService.verifyAttestation(body.attestationCerts)
        return ResponseEntity.ok(BindingCsrResponseJ(certificate, signedPublicKey))
            .also { log.info("/binding/create returns ok: {}", it) }
    }

    @Operation(
        summary = "Confirm binding",
        description = "Confirm the reception of the binding certificate.",
        security = [SecurityRequirement(name = "xAuthToken")],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Confirmation has been recorded",
            ),
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
        ],
    )
    @PostMapping("/binding/confirm")
    @PreAuthorize("hasAuthority(\"PUPIL\")")
    fun confirmBinding(
        @RequestBody body: BindingConfirmRequestJ,
        principal: Principal,
        request: HttpServletRequest,
    ): ResponseEntity<BindingConfirmResponseJ> {
        log.info("/binding/confirm called for {} with {}", principal, body)
        if (!body.success) {
            return ResponseEntity.badRequest().build<BindingConfirmResponseJ>()
                .also { log.info("/binding/confirm returns success not set: {}", it) }
        }
        // We want the client to not need to authenticate again when using the PupilIdController
        // so we'll set the expected authentication token into the current security context
        // and do not logout the client (previously, "request.logout()" has been called here)
        if (principal is ExtNonceAuthnToken) {
            extNonceAuthnService.invalidateNonce(principal.credentials.toString())
            SecurityContextHolder.getContext().authentication =
                DeviceBindingAuthnToken("", principal.principal.toString(), byteArrayOf())
        }
        return ResponseEntity.ok(BindingConfirmResponseJ(true))
            .also { log.info("/binding/confirm returns ok: {}", it) }
    }

}
