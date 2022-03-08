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
        val signedPublicKey = certificateService.verifyAttestation(body.attestationCerts)
        return ResponseEntity.ok(BindingCsrResponse(certificate, signedPublicKey))
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
                .also { log.info("/binding/confirm returns HTTP 400: Success not set") }
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
            example = "MIHNMHQCAQAwEjEQMA4GA1UEAwwHU3ViamVjdDBZMBMGByqGSM49AgEGCCqGSM49" +
                    "AwEHA0IABEgRPVMGMgkAilfugC/3mncR8mot9gsC4/bJmlW0ugpxRMiIgi3srUmI" +
                    "lCMgTN9hMPGEAXdPd0Hvize9o9vuezagADAKBggqhkjOPQQDAgNJADBGAiEA2l1X" +
                    "vS1c1j/f6SN0AwTdJZNvTwnZP3tRQyNpzQMZMnMCIQDepERQmECr3mqFGS4AQzSn" +
                    "WpwZZBjGtmU1NWiK/E92Ew==",
            nullable = false,
        )
        val csr: ByteArray,
        @Schema(description = "Name of the mobile device", example = "Pixel 3", nullable = false)
        val deviceName: String,
        @Schema(
            description = "The Key Attestation (Android) or Device Attestation (iOS) structure of the client device",
            example = "[MIICpjCCAkqgAwIBAgIBATAMBggqhkjOPQQDAgUAMD8xEjAQBgNVBAwMCVN0cm9u" +
                    "Z0JveDEpMCcGA1UEBRMgMDY4NDJmODRiY2JhZGJkMTk2NDA1YmZkNmE2MzQ5ZWIw" +
                    "HhcNNzAwMTAxMDAwMDAwWhcNNDgwMTAxMDAwMDAwWjAfMR0wGwYDVQQDExRBbmRy" +
                    "b2lkIEtleXN0b3JlIEtleTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABD1auUFh" +
                    "E6prEafZ90OHrq6CPZS6+hTJ3HLmeqOw2OCytf0NaCLLz6DMLe1GV3EWxCDGi1UH" +
                    "e10UO5zwx/2OyFCjggFTMIIBTzAOBgNVHQ8BAf8EBAMCB4AwggE7BgorBgEEAdZ5" +
                    "AgERBIIBKzCCAScCAWQKAQICAWQKAQIEJDQ1Y2ZiYWRhLWE5NTItNGVhNS05M2Jj" +
                    "LWYyZWQzNjVlOGRiOAQAMEy/hUVIBEYwRDEeMBwEFmF0LmFzaXRwbHVzLmJpb21l" +
                    "dHJpY3MCAgFAMSIEIEFfrT4RcXh0HaTOlPpeZXwPjA8Z06Nw7B6ZSBe/nLXrMIGi" +
                    "oQUxAwIBAqIDAgEDowQCAgEApQUxAwIBBKoDAgEBv4N4AwIBAr+FPgMCAQC/hUBM" +
                    "MEoEIA9udcgBg7XewHSwBU1CcemTievksTawgZ3h8VC6D/nXAQH/CgEABCBmOJbI" +
                    "61T3+Ji7mfx/sIEdmd7/o4Vwizd3ttcqU2kaH7+FQQUCAwHUwL+FQgUCAwMVf7+F" +
                    "TgYCBAE0ZaG/hU8GAgQBNGWcMAwGCCqGSM49BAMCBQADSAAwRQIgae9OOc3Nwhak" +
                    "cZCAeA9IXRWyBauT47ADg9Dy9EtasnMCIQDH/fwrI3O45Oqo6OQdBpqNGI77Gprv" +
                    "rXoKs6kqldIjmA==]",
        )
        val attestationCerts: List<ByteArray>,
    )

    @Schema(description = "Response to the CSR, containing the binding certificate")
    data class BindingCsrResponse(
        @Schema(
            description = "The signed binding certificate, to be stored on the mobile device",
            example = "MIIBFzCBvaADAgECAgjWVAvsBy5UXDAKBggqhkjOPQQDAjASMRAwDgYDVQQDDAdT" +
                    "dWJqZWN0MB4XDTIyMDIyMjE1MzM0NVoXDTIyMDIyMjE1MzQ0NVowETEPMA0GA1UE" +
                    "AwwGSXNzdWVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPnNczNYC/8QwBXZr" +
                    "KqBDdSwvzHQQKOi8UWpsy+33uW2zJorQXgAljj0qxCmVlgPs5FAoF7zzQbM/4pF1" +
                    "DfK+6jAKBggqhkjOPQQDAgNJADBGAiEAs9sOHPs3vuHP5zbaTUTxC2j4a/afLfW1" +
                    "GlMJdHGwsToCIQCiAbOdx7Bth+T7MjQhv9hsYo0zDzuMBvxYKF+pbNtJdg==",
            nullable = false,
        )
        val certificate: ByteArray,
        @Schema(
            description = "The signed public key as an JWS, if the attestation from the client was correct, otherwise `null`",
            example = "eyJhbGciOiJFUzI1NiJ9.eyJwayI6IkJFWHlSS3JVdWh6RHluV1N3YTJEcytUanN" +
                    "zaEVQRDBOZEFGUDBHVVlha2krQUZoTUxxT0hYUnN3MUgreFFNM2JmYXRoTlhJY3h" +
                    "icWg3N1dPaVJUMHFZTT0ifQ.OBdGISyFNba1YpPEMj8Su-wWgSKDEBuFNAUHAggu" +
                    "gQ1bbT01cjuLxphmiGnHYuXXi86wSg_JkCOcgV-acUrysQ",
            nullable = true,
        )
        val attestedPublicKey: String?,
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
