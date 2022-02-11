package at.asitplus.wallet.backend

import io.swagger.v3.oas.annotations.Operation
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
        description = "Get parameters to initiate a binding between app and identity."
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
        description = "Post certification request to get a binding."
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
        deviceBindingStorageService.store(principal.name, certificate)
        return ResponseEntity.ok(BindingCsrResponse(certificate))
            .also { request.logout() }
    }

    data class BindingParamsRequest(
        val deviceName: String,
    )

    data class BindingParamsResponse(
        val challenge: ByteArray,
    )

    data class BindingCsrRequest(
        val challenge: ByteArray,
        val csr: ByteArray,
    )

    data class BindingCsrResponse(
        val certificate: ByteArray,
    )

}