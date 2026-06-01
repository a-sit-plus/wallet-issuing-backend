package at.asitplus.wallet.backend.controller

import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.auth.SpringSecurityAuthenticationSupplier
import at.asitplus.wallet.backend.service.RevocationService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.ModelAndView

/**
 * Implements the self-revocation mechanism (for debug purposes), see `revocation.html`
 */
@RestController
class RevocationController(
    private val revocationService: RevocationService,
) {

    @PreAuthorize("isAuthenticated()")
    @GetMapping(Paths.RevocationUrl)
    fun list(
        model: ModelMap,
        authentication: Authentication?,
    ): ModelAndView = runBlocking {
        val authenticatedUser = SpringSecurityAuthenticationSupplier.toOidcUserInfoExtended(authentication)
        Napier.i("${Paths.RevocationUrl} called with ${authenticatedUser?.userInfo?.subject}")
        authenticatedUser?.let {
            model["credentials"] = revocationService.getAllNonRevokedForUser(it).sortedBy { it.revocationListIndex }
            model["revokedCredentials"] = revocationService.getAllRevokedForUser(it).sortedBy { it.revocationListIndex }
        }
        ModelAndView("revocation")
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("${Paths.RevokeUrl}/{id}", produces = [APPLICATION_JSON_VALUE])
    fun revoke(
        @PathVariable id: String,
        authentication: Authentication?,
    ): ResponseEntity<String> = runBlocking {
        val authenticatedUser = SpringSecurityAuthenticationSupplier.toOidcUserInfoExtended(authentication)
        Napier.i("${Paths.RevokeUrl}/$id called with ${authenticatedUser?.userInfo?.subject}")
        authenticatedUser?.let {
            if (revocationService.revoke(id.toLong(), it)) {
                Napier.d("${Paths.RevokeUrl}/$id returns OK")
                ResponseEntity.ok().build()
            } else {
                Napier.d("${Paths.RevokeUrl}/$id returns NOT_FOUND")
                ResponseEntity.notFound().build()
            }
        } ?: ResponseEntity.notFound().build()
    }

}

