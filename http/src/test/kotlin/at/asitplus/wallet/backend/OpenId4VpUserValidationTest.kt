package at.asitplus.wallet.backend

import at.asitplus.catching
import at.asitplus.openid.AuthenticationRequestParameters
import at.asitplus.wallet.backend.controller.toUser
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.validation.CheckOutcome
import at.asitplus.wallet.lib.validation.ProtocolResponseChecks
import at.asitplus.wallet.lib.validation.ValidationReport
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import kotlin.time.Clock

class OpenId4VpUserValidationTest {
    @Test fun `response-level rejection supplies its cause without authenticating`() {
        val user = response(CheckOutcome.Failed(IllegalArgumentException("Required PID claims missing"))).toUser()
        user.presentationError shouldBe "Required PID claims missing"
        user.isAccepted shouldBe false
        user.credentials.shouldBeNull()
    }

    @Test fun `blocked response without a cause still names its rejecting check`() {
        val user = response(CheckOutcome.Blocked()).toUser()
        user.presentationError!! shouldContain "submissionRequirements"
        user.isAccepted shouldBe false
        user.credentials.shouldBeNull()
    }

    private fun response(submission: CheckOutcome) = AuthnResponseResult.Success(
        vpTokenResult = catching { error("Legacy projection must not supply the diagnostic") },
        request = AuthenticationRequestParameters(state = "test-state"),
        validationReport = ValidationReport.protocolResponse(
            ProtocolResponseChecks(
                parsing = CheckOutcome.Passed,
                encryption = CheckOutcome.NotApplicable,
                expectedOrigin = CheckOutcome.NotApplicable,
                requestState = CheckOutcome.Passed,
                submissionRequirements = submission,
            ),
            presentations = emptyList(),
            evaluatedAt = Clock.System.now(),
        ),
    )
}
