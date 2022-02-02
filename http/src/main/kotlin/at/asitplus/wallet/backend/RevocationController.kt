package at.asitplus.wallet.backend

import io.swagger.v3.oas.annotations.Operation
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class RevocationController {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @Operation(
        summary = "Revoke one or more device bindings for a pupil",
        description = "Revoke one or more device bindings for a pupil, either specified by their `bpk` or specified by their `bpk` and `deviceId`."
    )
    @PostMapping("/revoke/binding")
    fun revokeBinding(@RequestBody body: RevocationRequest): ResponseEntity<RevocationResponse> {
        logger.info("/revoke/binding called with {}", body)
        return ResponseEntity.ok(RevocationResponse(true))
    }

    @Operation(
        summary = "Revoke all PupilId instances for a pupil",
        description = "Revoke all PupilId instances for one pupil, specified by their `bpk`."
    )
    @PostMapping("/revoke/pupilid")
    fun revokePupilId(@RequestBody body: RevocationRequest): ResponseEntity<RevocationResponse> {
        logger.info("/revoke/pupilid called with {}", body)
        if (body.deviceId != null)
            return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(RevocationResponse(true))
    }

    @Operation(
        summary = "Get a list of active devices for a pupil",
        description = "Get a list of devices for one pupil, specified by their `bpk`."
    )
    @GetMapping("/revoke/devices/{bpk}")
    fun readDevice(@PathVariable("bpk") bpk: String): ResponseEntity<DeviceListResponse> {
        logger.info("/revoke/devices called for '{}'", bpk)
        return ResponseEntity.ok(
            DeviceListResponse(
                listOf(
                    DeviceListResponseEntry("id1", "Pixel 3"),
                    DeviceListResponseEntry("id2", "iPhone 7")
                )
            )
        )
    }

    data class DeviceListResponse(
        val list: Collection<DeviceListResponseEntry>,
    )

    data class DeviceListResponseEntry(
        val id: String,
        val name: String,
    )

    data class RevocationRequest(
        val bpk: String,
        val deviceId: String? = null,
    )

    data class RevocationResponse(
        val success: Boolean,
    )

}