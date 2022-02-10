package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.model.IdentifierRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

@Controller
class RevokeListController {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Autowired
    private lateinit var configurationProperties: BackendConfigurationProperties

    @Autowired
    private lateinit var identifierRegistry: IdentifierRegistry

    @GetMapping("/revoke/list")
    fun revokeList(model: ModelMap): ModelAndView {
        log.info("/revoke/list called")
        return buildRevokeList(model)
    }

    @GetMapping("/revoke")
    fun revokeByVcId(model: ModelMap, @RequestParam("vcId") vcId: String): ModelAndView {
        log.info("/revoke called with vcId=$vcId")
        identifierRegistry.revoke(vcId)
        return buildRevokeList(model)
    }

    private fun buildRevokeList(model: ModelMap): ModelAndView {
        model["vcList"] = identifierRegistry.getAllNonRevokedWithDetails()
        model["revocationListUrl"] = "${configurationProperties.publicContext}/credentials/status/1"
        model["revokeActionUrl"] = "${configurationProperties.publicContext}/revoke"
        return ModelAndView("revoke_list", model)
    }

}