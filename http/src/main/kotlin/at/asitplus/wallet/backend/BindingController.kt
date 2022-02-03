package at.asitplus.wallet.backend

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
    @PostMapping("/binding/start")
    @PreAuthorize("hasAuthority(\"PUPIL\")")
    fun requestBindingParams(
        @RequestBody body: BindingParamsRequest,
        principal: Principal
    ): ResponseEntity<BindingParamsResponse> {
        logger.info("/binding/start called for {} with {}", principal, body)
        return ResponseEntity.ok(BindingParamsResponse(Random.nextBytes(32)))
    }

    @Operation(
        summary = "Create binding",
        description = "Post certification request to get a binding."
    )
    @PostMapping("/binding/create")
    @PreAuthorize("hasAuthority(\"PUPIL\")")
    fun postBindingCsr(@RequestBody body: BindingCsrRequest, principal: Principal): ResponseEntity<BindingCsrResponse> {
        // TODO verify challenge
        logger.info("/binding/create called for {} with {}", principal, body)
        //val auth = SecurityContextHolder.getContext().authentication
        return ResponseEntity.ok(BindingCsrResponse(Random.nextBytes(32)))
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