package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.NextMessage
import io.swagger.v3.oas.annotations.Operation
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
class PupilIdController(
    private val issueCredentialMessengerPupilId: IssueCredentialMessenger,
) {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @Operation(
        summary = "Issue credentials",
        description = "Issues a fresh instance of a PupilId to the Wallet app."
    )
    @PostMapping("/pupilid/issue")
    @PreAuthorize("hasAuthority(\"DEVICE_BINDING\")")
    fun issueCredential(
        @RequestBody body: String,
        principal: Principal
    ) = runBlocking {
        logger.info("/pupilid/issue called for {} with '{}'", principal, body)
        when (val result = issueCredentialMessengerPupilId.parseMessage(body)) {
            is NextMessage.Finished -> {
                logger.info("/pupilid/issue returning empty body, has finished")
                ResponseEntity.status(HttpStatus.OK).build()
            }
            is NextMessage.Send -> {
                logger.info("/pupilid/issue returning ${result.message}")
                ResponseEntity.ok(result.message)
            }
            is NextMessage.Error -> {
                logger.error("/pupilid/issue returning 400, incorrect protocol state")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
            }
            is NextMessage.SendProblemReport -> {
                logger.info("/pupilid/issue returning problem report ${result.message}")
                ResponseEntity.ok(result.message)
            }
            is NextMessage.ReceivedProblemReport -> {
                logger.info("/pupilid/issue received a problem report ${result.message}")
                ResponseEntity.ok().build()
            }
        }
    }

}