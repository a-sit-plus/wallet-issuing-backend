package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.AuthenticatedDeviceBindingUser
import at.asitplus.wallet.lib.agent.NextMessage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
class PupilIdController(
    private val pupilIdService: PupilIdService,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Operation(
        summary = "Issue credentials",
        description = "Issues a fresh instance of a PupilId to the Wallet app.",
        security = [SecurityRequirement(name = "deviceBinding")],
    )
    @PostMapping("/pupilid/issue")
    @PreAuthorize("hasAuthority(\"DEVICE_BINDING\")")
    fun issueCredential(
        @RequestBody body: String,
        authentication: Authentication,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        log.info("/pupilid/issue called for {} with '{}'", authentication, body)
        when (val result = pupilIdService.parseMessage(body)) {
            is NextMessage.Result<*> -> {
                log.info("/pupilid/issue returning empty body, has finished")
                return ResponseEntity.status(HttpStatus.OK).build<String>()
                    .also { request.logout() }
            }
            is NextMessage.Send -> {
                log.info("/pupilid/issue returning ${result.message}")
                return ResponseEntity.ok(result.message)
                    .also { request.logout() }
            }
            is NextMessage.Error -> {
                log.error("/pupilid/issue returning 400, incorrect protocol state")
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build<String>()
                    .also { request.logout() }
            }
            is NextMessage.SendProblemReport -> {
                log.info("/pupilid/issue returning problem report ${result.message}")
                return ResponseEntity.ok(result.message)
                    .also { request.logout() }
            }
            is NextMessage.ReceivedProblemReport -> {
                log.info("/pupilid/issue received a problem report ${result.message}")
                return ResponseEntity.ok().build<String>()
                    .also { request.logout() }
            }
            else -> {
                log.info("/pupilid/issue received something else: $result")
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build<String>()
                    .also { request.logout() }
            }
        }
    }

}