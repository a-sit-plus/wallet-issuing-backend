package at.asitplus.wallet.backend

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
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
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Operation(
        summary = "Initiate binding",
        description = "Get parameters to initiate a binding between a key on the mobile device and the authenticated user.",
        security = [SecurityRequirement(name = "extNonce")],
    )
    @PostMapping("/binding/start")
    @PreAuthorize("hasAuthority(\"PUPIL\")")
    fun requestBindingParams(
        @RequestBody body: BindingParamsRequest,
        principal: Principal
    ): ResponseEntity<BindingParamsResponse> {
        log.info("/binding/start called for {} with {}", principal, body)
        val challenge = challengeService.generate()
        return ResponseEntity.ok(BindingParamsResponse(challenge))
    }

    @Operation(
        summary = "Create binding",
        description = "Post certification request to get a binding.",
        security = [SecurityRequirement(name = "xAuthToken")],
    )
    @PostMapping("/binding/create")
    @PreAuthorize("hasAuthority(\"PUPIL\")")
    fun postBindingCsr(
        @RequestBody body: BindingCsrRequest,
        principal: Principal,
        request: HttpServletRequest,
    ): ResponseEntity<BindingCsrResponse> {
        log.info("/binding/create called for {} with {}", principal, body)
        if (!challengeService.verifyAndRemove(body.challenge)) {
            return ResponseEntity.badRequest().build()
        }
        val certificate = certificateService.verifyAndSign(body.csr)
            ?: return ResponseEntity.badRequest().build()
        deviceBindingStorageService.store(principal.name, certificate, body.deviceName)
        return ResponseEntity.ok(BindingCsrResponse(certificate))
            .also { request.logout() }
    }

    @Schema(description = "Request to get parameters for a new binding")
    data class BindingParamsRequest(
        @Schema(description = "Name of the mobile device", example = "Pixel 3")
        val deviceName: String,
    )

    @Schema(description = "List of registered mobile devices")
    data class BindingParamsResponse(
        @Schema(description = "Random challenge to be included in the next request")
        val challenge: ByteArray,
    )

    @Schema(description = "Request to sign a public key")
    data class BindingCsrRequest(
        @Schema(description = "Challenge from previous response")
        val challenge: ByteArray,
        @Schema(description = "Certification Signing Request in PKCS#10 format")
        val csr: ByteArray,
        @Schema(description = "Name of the mobile device", example = "Pixel 3")
        val deviceName: String,
    )

    @Schema(description = "Final response of the binding process")
    data class BindingCsrResponse(
        @Schema(description = "The signed binding certificate, to be stored on the mobile device")
        val certificate: ByteArray,
    )

}