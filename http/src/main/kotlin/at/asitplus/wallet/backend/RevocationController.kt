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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


/**
 * Implements the revocation endpoints, called from other backend services:
 * - Revoke a device binding
 * - Revoke one or more credentials
 */
@RestController
class RevocationController(
    private val bindingStorageService: DeviceBindingStorageService,
    private val revocationService: RevocationService,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Operation(
        summary = "Revoke one or more device bindings for a pupil",
        description = "Revoke one or more device bindings for a pupil, either specified by their `bpk` or specified by their `bpk` and `deviceId`.",
        security = [SecurityRequirement(name = "apiKey")],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Some device bindings have been revoked",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Client is not authenticated, i.e. it needs to send API-Key in header `X-API-Key`",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(
                responseCode = "404",
                description = "No device binding has been found for the input value",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
        ],
    )
    @PostMapping("/revoke/binding")
    @PreAuthorize("hasAuthority(\"REVOCATION\")")
    fun revokeBinding(@RequestBody body: RevocationRequest): ResponseEntity<RevocationResponse> {
        log.info("/revoke/binding called with {}", body)
        val count = revocationService.revokeBinding(body.bpk, body.deviceId)
        if (count == 0)
            return ResponseEntity.notFound().build<RevocationResponse>()
                .also { log.info("/revoke/binding returns HTTP 404") }
        return ResponseEntity.ok(RevocationResponse(count))
            .also { log.info("/revoke/binding returns HTTP 200: {}", it) }
    }

    @Operation(
        summary = "Revoke all PupilId instances for a pupil",
        description = "Revoke all PupilId instances for one pupil, specified by their `bpk`.",
        security = [SecurityRequirement(name = "apiKey")],
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [ExampleObject(value = "{\n  \"bpk\": \"BF:j/NxdRQhp+tNyE9WhHdBSYuy3hA=\"\n}\n")]
            )]
        ),
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Some credentials have been revoked",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Value for `deviceId` is not valid, should be `null`",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(
                responseCode = "403",
                description = "Client is not authenticated, i.e. it needs to send API-Key in header `X-API-Key`",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(
                responseCode = "404",
                description = "No PupilId has been found for the input value",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
        ],
    )
    @PostMapping("/revoke/pupilid")
    @PreAuthorize("hasAuthority(\"REVOCATION\")")
    fun revokePupilId(@RequestBody body: RevocationRequest): ResponseEntity<RevocationResponse> {
        log.info("/revoke/pupilid called with {}", body)
        if (body.deviceId != null)
            return ResponseEntity.badRequest().build<RevocationResponse>()
                .also { log.info("/revoke/pupilid returns HTTP 400, deviceId has been set") }
        val count = revocationService.revokeCredentialsByBpk(body.bpk)
        if (count == 0)
            return ResponseEntity.badRequest().build<RevocationResponse>()
                .also { log.info("/revoke/pupilid returns HTTP 404") }
        return ResponseEntity.ok(RevocationResponse(count))
            .also { log.info("/revoke/pupilid returns HTTP 200: {}", it) }
    }

    @Operation(
        summary = "Get a list of active devices for a pupil",
        description = "Get a list of devices for one pupil, specified by their `bpk`.",
        security = [SecurityRequirement(name = "apiKey")],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Devices have been found for pupil with `bpk`",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Value for `bpk` is not valid, should not be blank",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(
                responseCode = "403",
                description = "Client is not authenticated, i.e. it needs to send API-Key in header `X-API-Key`",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(
                responseCode = "404",
                description = "No devices found for pupil with `bpk`",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
        ],
    )
    @GetMapping("/revoke/devices")
    @PreAuthorize("hasAuthority(\"REVOCATION\")")
    fun readDevice(@RequestParam("bpk") bpk: String): ResponseEntity<DeviceListResponse> {
        log.info("/revoke/devices called for bpk '{}'", bpk)
        if (bpk.isBlank()) {
            return ResponseEntity.badRequest().build<DeviceListResponse>()
                .also { log.info("/revoke/devices returns HTTP 400") }
        }
        val list = bindingStorageService.lookupDevices(bpk)
        if (list.isEmpty()) {
            return ResponseEntity.notFound().build<DeviceListResponse>()
                .also { log.info("/revoke/devices returns HTTP 404") }
        }
        return ResponseEntity.ok(
            DeviceListResponse(
                list.map { DeviceListResponseEntry(it.deviceId, it.deviceName) }
            )
        ).also { log.info("/revoke/devices returns HTTP 200: {}", it) }
    }

    @Schema(description = "List of registered mobile devices")
    data class DeviceListResponse(
        @Schema(description = "List of registered mobile devices", nullable = false)
        val list: Collection<DeviceListResponseEntry>,
    )

    @Schema(description = "Single registered mobile device")
    data class DeviceListResponseEntry(
        @Schema(
            description = "`deviceId` of the device",
            example = "81113d6f-aa19-438a-96e7-abd1ee56d5ae",
            nullable = false
        )
        val id: String,
        @Schema(description = "Name of the device", example = "Pixel 3", nullable = false)
        val name: String,
    )

    @Schema(description = "Request for revocation of device binding or PupilId")
    data class RevocationRequest(
        @Schema(description = "`bpk` of the pupil", example = "BF:j/NxdRQhp+tNyE9WhHdBSYuy3hA=", nullable = false)
        val bpk: String,
        @Schema(
            description = "`deviceId` of the device",
            example = "81113d6f-aa19-438a-96e7-abd1ee56d5ae",
            nullable = true
        )
        val deviceId: String? = null,
    )

    @Schema(description = "Response of a revocation call")
    data class RevocationResponse(
        @Schema(description = "Number of credentials that have been revoked", example = "1", nullable = false)
        val count: Int,
    )

}