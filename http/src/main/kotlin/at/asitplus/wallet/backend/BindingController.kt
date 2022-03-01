package at.asitplus.wallet.backend

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
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
        @RequestBody body: BindingParamsRequest,
        principal: Principal
    ): ResponseEntity<BindingParamsResponse> {
        log.info("/binding/start called for {} with {}", principal, body)
        val challenge = challengeService.generate()
        return ResponseEntity.ok(BindingParamsResponse(challenge))
            .also { log.info("/binding/start returns HTTP 200: {}", it) }
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
        @RequestBody body: BindingCsrRequest,
        principal: Principal,
        request: HttpServletRequest,
    ): ResponseEntity<BindingCsrResponse> {
        log.info("/binding/create called for {} with {}", principal, body)
        if (!challengeService.verifyAndRemove(body.challenge)) {
            return ResponseEntity.badRequest().build<BindingCsrResponse>()
                .also { log.info("/binding/create returns HTTP 400: Challenge invalid") }
        }
        val certificate = certificateService.verifyAndSign(body.csr)
            ?: return ResponseEntity.badRequest().build<BindingCsrResponse>()
                .also { log.info("/binding/create returns HTTP 400: CSR invalid") }
        deviceBindingStorageService.store(principal.name, certificate, body.deviceName)
        return ResponseEntity.ok(BindingCsrResponse(certificate))
            .also { log.info("/binding/create returns HTTP 200: {}", it) }
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
        @RequestBody body: BindingConfirmRequest,
        principal: Principal,
        request: HttpServletRequest,
    ): ResponseEntity<BindingConfirmResponse> {
        log.info("/binding/confirm called for {} with {}", principal, body)
        if (!body.success) {
            return ResponseEntity.badRequest().build<BindingConfirmResponse>()
                .also { log.info("/binding/create returns HTTP 400: Success not set") }
        }
        return ResponseEntity.ok(BindingConfirmResponse(true))
            .also { request.logout() }
            .also { log.info("/binding/confirm returns HTTP 200: {}", it) }
    }

    @Schema(description = "Request to get parameters for a new binding")
    data class BindingParamsRequest(
        @Schema(description = "Name of the mobile device", example = "Pixel 3", nullable = false)
        val deviceName: String,
    )

    @Schema(description = "List of registered mobile devices")
    data class BindingParamsResponse(
        @Schema(
            description = "Random challenge to be included in the next request",
            example = "6j2a9M7P1J9bOUuGe5Tpto7Ylz+2DtbH54jdHh2YO/Y=",
            nullable = false,
        )
        val challenge: ByteArray,
    )

    @Schema(description = "Request to sign a public key")
    data class BindingCsrRequest(
        @Schema(
            description = "Challenge from previous response",
            example = "6j2a9M7P1J9bOUuGe5Tpto7Ylz+2DtbH54jdHh2YO/Y=",
            nullable = false,
        )
        val challenge: ByteArray,
        @Schema(
            description = "Certification Signing Request in PKCS#10 format",
            example = "MIHNMHQCAQAwEjEQMA4GA1UEAwwHU3ViamVjdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABEgRPVMGMgkAilfugC/3mncR8mot9gsC4/bJmlW0ugpxRMiIgi3srUmIlCMgTN9hMPGEAXdPd0Hvize9o9vuezagADAKBggqhkjOPQQDAgNJADBGAiEA2l1XvS1c1j/f6SN0AwTdJZNvTwnZP3tRQyNpzQMZMnMCIQDepERQmECr3mqFGS4AQzSnWpwZZBjGtmU1NWiK/E92Ew==",
            nullable = false,
        )
        val csr: ByteArray,
        @Schema(description = "Name of the mobile device", example = "Pixel 3", nullable = false)
        val deviceName: String,
    )

    @Schema(description = "Resonse to the CSR, containing the binding certificate")
    data class BindingCsrResponse(
        @Schema(
            description = "The signed binding certificate, to be stored on the mobile device",
            example = "MIIBFzCBvaADAgECAgjWVAvsBy5UXDAKBggqhkjOPQQDAjASMRAwDgYDVQQDDAdTdWJqZWN0MB4XDTIyMDIyMjE1MzM0NVoXDTIyMDIyMjE1MzQ0NVowETEPMA0GA1UEAwwGSXNzdWVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPnNczNYC/8QwBXZrKqBDdSwvzHQQKOi8UWpsy+33uW2zJorQXgAljj0qxCmVlgPs5FAoF7zzQbM/4pF1DfK+6jAKBggqhkjOPQQDAgNJADBGAiEAs9sOHPs3vuHP5zbaTUTxC2j4a/afLfW1GlMJdHGwsToCIQCiAbOdx7Bth+T7MjQhv9hsYo0zDzuMBvxYKF+pbNtJdg==",
            nullable = false
        )
        val certificate: ByteArray,
    )

    @Schema(description = "Request to confirm the binding")
    data class BindingConfirmRequest(
        val success: Boolean
    )

    @Schema(description = "Response to confirmation")
    data class BindingConfirmResponse(
        val success: Boolean
    )

}
