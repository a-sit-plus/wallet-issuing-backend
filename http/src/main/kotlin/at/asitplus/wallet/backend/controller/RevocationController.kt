package at.asitplus.wallet.backend.controller

import at.asitplus.openid.CredentialOffer
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.service.RevocationService
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.ModelAndView

/**
 * Implements controller for OpenID 4 Verifiable Credential Issuance
 */
@RestController
class RevocationController(
    private val backendConfigurationProperties: BackendConfigurationProperties,
    private val authenticationSupplier: AuthenticationSupplier,
    private val issuerCredentialStore: IssuerCredentialStore,
    private val revocationService: RevocationService,
) {

    private val mapNonceToOffer = mutableMapOf<String, CredentialOffer>()

    @GetMapping("/revocation")
    fun index(model: ModelMap): ModelAndView = runBlocking {
        val principal = authenticationSupplier.getCurrentUserOidcDetails()
        Napier.i("/revocation called with $principal")
        principal?.let {
            model["credentials"] = revocationService.getAllNonRevokedForUser(it)
        }
        ModelAndView("revocation")
    }

}

