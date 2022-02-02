package at.asitplus.wallet.backend

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class RevocationController {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @PostMapping("/revoke/binding")
    fun revokeBinding(@RequestBody body: RevocationRequest): ResponseEntity<RevocationResponse> {
        logger.info("/revoke/binding called with {}", body)
        return ResponseEntity.ok(RevocationResponse(true))
    }

    @PostMapping("/revoke/pupilid")
    fun revokePupilId(@RequestBody body: RevocationRequest): ResponseEntity<RevocationResponse> {
        logger.info("/revoke/pupilid called with {}", body)
        if (body.deviceId != null)
            return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(RevocationResponse(true))
    }

    data class RevocationRequest(
        val bpk: String,
        val deviceId: String? = null,
    )

    data class RevocationResponse(
        val success: Boolean,
    )

}