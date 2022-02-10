package at.asitplus.wallet.backend

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class RevocationController {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Operation(
        summary = "Revoke one or more device bindings for a pupil",
        description = "Revoke one or more device bindings for a pupil, either specified by their `bpk` or specified by their `bpk` and `deviceId`."
    )
    @PostMapping("/revoke/binding")
    fun revokeBinding(@RequestBody body: RevocationRequest): ResponseEntity<RevocationResponse> {
        log.info("/revoke/binding called with {}", body)
        return ResponseEntity.ok(RevocationResponse(true))
    }

    @Operation(
        summary = "Revoke all PupilId instances for a pupil",
        description = "Revoke all PupilId instances for one pupil, specified by their `bpk`."
    )
    @PostMapping("/revoke/pupilid")
    fun revokePupilId(@RequestBody body: RevocationRequest): ResponseEntity<RevocationResponse> {
        log.info("/revoke/pupilid called with {}", body)
        if (body.deviceId != null)
            return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(RevocationResponse(true))
    }

    @Operation(
        summary = "Get a list of active devices for a pupil",
        description = "Get a list of devices for one pupil, specified by their `bpk`."
    )
    @GetMapping("/revoke/devices")
    fun readDevice(@RequestParam("bpk") bpk: String): ResponseEntity<DeviceListResponse> {
        log.info("/revoke/devices called for bpk '{}'", bpk)
        return ResponseEntity.ok(
            DeviceListResponse(
                listOf(
                    DeviceListResponseEntry("id1", "Pixel 3"),
                    DeviceListResponseEntry("id2", "iPhone 7")
                )
            )
        )
    }

    @Schema(description = "List of registered mobile devices")
    data class DeviceListResponse(
        val list: Collection<DeviceListResponseEntry>,
    )

    @Schema(description = "Single registered mobile device")
    data class DeviceListResponseEntry(
        @Schema(example = "81113d6f-aa19-438a-96e7-abd1ee56d5ae")
        val id: String,
        @Schema(example = "Pixel 3")
        val name: String,
    )

    @Schema(description = "Request for revocation of device binding or PupilId")
    data class RevocationRequest(
        @Schema(example = "BF:j/NxdRQhp+tNyE9WhHdBSYuy3hA=")
        val bpk: String,
        @Schema(example = "81113d6f-aa19-438a-96e7-abd1ee56d5ae")
        val deviceId: String? = null,
    )

    @Schema(description = "Response of a revocation call")
    data class RevocationResponse(
        @Schema(example = "true")
        val success: Boolean,
    )

}