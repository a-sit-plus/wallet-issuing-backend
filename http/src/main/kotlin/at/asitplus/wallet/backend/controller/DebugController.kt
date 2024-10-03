package at.asitplus.wallet.backend.controller

import at.asitplus.wallet.backend.Extensions.appendPath
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.lib.agent.TimePeriodProvider
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import org.springframework.stereotype.Controller
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

@Controller
class DebugController(
    private val configurationProperties: BackendConfigurationProperties,
    private val revocationService: RevocationService,
    private val timePeriodProvider: TimePeriodProvider,
) {

    @GetMapping("/debug/credential/list")
    fun revokeList(model: ModelMap): ModelAndView {
        if (!configurationProperties.debug.enabled) return ModelAndView("index", model)
        Napier.i("/debug/credential/list called")
        return buildCredentialList(model)
    }

    @GetMapping("/debug/credential/revoke")
    fun revokeByVcId(model: ModelMap, @RequestParam("vcId") vcId: String): ModelAndView {
        if (!configurationProperties.debug.enabled) return ModelAndView("index", model)
        Napier.i("/debug/credential/revoke called with vcId='{}'")
        Napier.v("vcId='$vcId'")
        revocationService.revokeCredentialsByVcId(
            vcId,
            timePeriodProvider.getTimePeriodFor(Clock.System.now())
        )
        return ModelAndView("redirect:/debug/credential/list")
    }

    private fun buildCredentialList(model: ModelMap): ModelAndView {
        val vcList = revocationService.getAllNonRevokedWithDetails().map {
            CredentialListDto(
                vcId = it.vcId.substring(0, 20) + "...",
                issuanceDate = it.createdOn.toString(),
                attributeName = it.attributeName,
                subjectId = it.subjectId,
            )
        }
        model["vcList"] = vcList
        model["revokeActionUrl"] = appendPath(
            configurationProperties.publicContext,
            "debug", "credential", "revoke"
        )
        return ModelAndView("credential_list", model)
    }

}


/**
 * Used in "credential_list.html"
 */
data class CredentialListDto(
    val vcId: String,
    val issuanceDate: String,
    val attributeName: String,
    val subjectId: String,
)
