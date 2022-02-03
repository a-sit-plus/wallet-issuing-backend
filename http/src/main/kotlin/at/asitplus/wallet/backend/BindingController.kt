package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.encodeBase16
import io.swagger.v3.oas.annotations.Operation
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import kotlin.random.Random


@RestController
class BindingController {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @Operation(
        summary = "Initiate binding",
        description = "Get parameters to initiate a binding between app and identity."
    )
    @PostMapping("/binding/create")
    @PreAuthorize("isFullyAuthenticated()")
    fun createBinding(@RequestBody body: BindingRequest, principal: Principal): ResponseEntity<BindingResponse> {
        logger.info("/binding/create called for {} with {}", principal, body)
        //val auth = SecurityContextHolder.getContext().authentication
        return ResponseEntity.ok(BindingResponse(Random.nextBytes(32).encodeBase16()))
    }

    data class BindingRequest(
        val deviceName: String,
    )

    data class BindingResponse(
        val challenge: String,
    )

}