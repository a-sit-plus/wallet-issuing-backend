package at.asitplus.wallet.backend.controller

import at.asitplus.wallet.backend.Extensions.sha256
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.MediaTypes
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListAggregation
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import org.apache.tomcat.websocket.AuthenticationException
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter
import org.springframework.security.web.WebAttributes
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.ModelAndView
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.readText
import kotlin.time.toJavaDuration

/**
 * Public endpoints, available without authentication:
 * - Revocation list for Verifiable Credentials (RevocationList2020)
 */
@RestController
class PublicController(
    private val issuer: Issuer,
    private val configurationProperties: BackendConfigurationProperties,
    private val clientRegistrations: InMemoryClientRegistrationRepository?,
) {

    @GetMapping("/credentials/status/current", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getStatutsListAggregation(): ResponseEntity<StatusListAggregation> = runBlocking {
        Napier.i("/credentials/status/current called")
        val rl = issuer.provideStatusListAggregation()
        Napier.i("/credentials/status/current returns $rl")
        ResponseEntity.ok(rl)
    }

    data class OAuth2ClientRegistration(
        val name: String, val url: String,
    )

    @RequestMapping("/login")
    fun login(
        model: ModelMap,
        request: HttpServletRequest,
        @RequestParam("error", required = false) error: String? = null,
    ): ModelAndView {
        model["oauthUrls"] = clientRegistrations?.map {
            OAuth2ClientRegistration(it.clientName, it.loginUrl())
        }
        if (error != null) {
            // from DefaultLoginPageGeneratingFilter
            model["loginError"] = (request.getSession(false)
                ?.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION) as? AuthenticationException)?.message?.ifEmpty { null }
                ?: "Invalid credentials"
        }
        return ModelAndView("login", model)
    }

    private fun ClientRegistration.loginUrl(): String =
        "${OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI}/${registrationId}"

    @GetMapping("/credentials/status/{timePeriod}")
    fun getVcRevocationList(@PathVariable timePeriod: Int, request: WebRequest): ResponseEntity<String> = runBlocking {
        Napier.i("/credentials/status/$timePeriod called")
        val path = Path(configurationProperties.revocationList.path, timePeriod.toString())
        val content = if (path.exists() && path.isReadable()) path.readText() else null
        if (content.isNullOrEmpty()) {
            Napier.w("/credentials/status/$timePeriod returns HTTP 404")
            return@runBlocking ResponseEntity.notFound().build()
        }
        val etag = content.encodeToByteArray().sha256().encodeToString(Base16()).uppercase()
        val cacheControl =
            CacheControl.maxAge(configurationProperties.revocationList.regularWriteTimeoutDuration.toJavaDuration())
        // Spring (or Tomcat?) appends "-gzip" to the ETag set by us, so we'll need to check that variant too
        if (request.checkNotModified(etag, path.toFile().lastModified())
            || request.checkNotModified("${etag}-gzip", path.toFile().lastModified())
        ) {
            Napier.d("/credentials/status/$timePeriod returns HTTP 304")
            return@runBlocking ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .cacheControl(cacheControl)
                .contentType(MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_JWT))
                .build()
        }
        Napier.i("/credentials/status/$timePeriod returns ${content.count()} chars")
        return@runBlocking ResponseEntity.ok()
            .cacheControl(cacheControl)
            .contentType(MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_JWT))
            .body(content)
    }

}