package at.asitplus.wallet.backend.controller

import at.asitplus.catching
import at.asitplus.openid.OpenIdConstants
import at.asitplus.openid.RequestParameters
import at.asitplus.openid.TokenRequestParameters
import at.asitplus.openid.decode
import at.asitplus.openid.decodeFromFormUrlEncoded
import at.asitplus.wallet.backend.Extensions.toRequestInfo
import at.asitplus.wallet.backend.Extensions.toResponseEntity
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.auth.SpringSecurityAuthenticationSupplier
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oauth2.toHttpResponse
import at.asitplus.wallet.lib.oidvci.OAuth2Exception
import io.github.aakira.napier.Napier
import io.ktor.http.*
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.ModelAndView


/**
 * Implements controller for OAuth2, mapping the public functions of [SimpleAuthorizationService].
 */
@RestController
class OAuth2Controller(
    private val authorizationService: SimpleAuthorizationService,
) {

    @GetMapping(OpenIdConstants.PATH_WELL_KNOWN_OPENID_CONFIGURATION, produces = [APPLICATION_JSON_VALUE])
    suspend fun openidMetadata(): ResponseEntity<String> {
        return authorizationService.metadata()
            .also { Napier.i("${OpenIdConstants.PATH_WELL_KNOWN_OPENID_CONFIGURATION} returns $it") }
            .toHttpResponse().toResponseEntity()
    }

    @GetMapping(OpenIdConstants.PATH_WELL_KNOWN_OAUTH_AUTHORIZATION_SERVER, produces = [APPLICATION_JSON_VALUE])
    suspend fun oauthMetadata(): ResponseEntity<String> {
        return authorizationService.metadata()
            .also { Napier.i("${OpenIdConstants.PATH_WELL_KNOWN_OAUTH_AUTHORIZATION_SERVER} returns $it") }
            .toHttpResponse().toResponseEntity()
    }

    /**
     * Called by the Wallet to get a challenge for its Client Attestation PoP JWT,
     * see [SimpleAuthorizationService.attestationChallenge].
     * Not available without wallet attestation, where no `challenge_endpoint` is advertised.
     */
    @PostMapping(Paths.ChallengeUrl, produces = [APPLICATION_JSON_VALUE])
    suspend fun attestationChallenge(): ResponseEntity<String> {
        Napier.i("${Paths.ChallengeUrl} called")
        val result = authorizationService.attestationChallenge().getOrElse {
            Napier.w("${Paths.ChallengeUrl} got error", it)
            throw it
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        Napier.d("${Paths.ChallengeUrl} returns $result")
        return result.toHttpResponse().toResponseEntity()
    }

    /**
     * Called by the Wallet when pushing an authorization request, see [SimpleAuthorizationService.par]
     */
    @PostMapping(Paths.ParUrl, produces = [APPLICATION_JSON_VALUE])
    suspend fun par(
        @RequestBody requestBody: String,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        Napier.i("${Paths.ParUrl} called")
        Napier.v("${Paths.ParUrl} called with $requestBody")
        val params: RequestParameters = requestBody.decodeFromFormUrlEncoded()
            ?: throw OAuth2Exception.InvalidRequest()
        val result = authorizationService.parWithDpopNonce(
            request = params,
            httpRequest = request.toRequestInfo().also {
                Napier.v("${Paths.ParUrl} called with $it")
            },
        ).getOrElse {
            Napier.w("${Paths.ParUrl} got error", it)
            throw it
        }
        Napier.d("${Paths.ParUrl} returns $result")
        return result.toHttpResponse().toResponseEntity()
    }

    /**
     * Logs out the user from the Spring Boot session, so that new requests need to be authorized again,
     * using the configured OAuth2 AS. Subsequent requests to [token] and `/credential` are secured
     * by the authorization code returned here.
     * See [SimpleAuthorizationService.authorize].
     */
    // TODO add "PreAuthorize" annotation?
    @RequestMapping(Paths.AuthorizeUrl, method = [RequestMethod.POST, RequestMethod.GET])
    suspend fun authorize(
        @RequestParam requestParams: Map<String, String>,
        @RequestBody requestBody: String?,
        request: HttpServletRequest,
        model: ModelMap,
        authentication: Authentication? = null,
    ): Any {
        Napier.i("${Paths.AuthorizeUrl} called")
        Napier.v("${Paths.AuthorizeUrl} called with $requestParams and $requestBody")
        val params: RequestParameters =
            if (requestBody.isNullOrEmpty()) requestParams.decode()
            else requestBody.decodeFromFormUrlEncoded()

        val result = authorizationService.authorize(params) {
            catching {
                SpringSecurityAuthenticationSupplier.toOidcUserInfoExtended(authentication)
                    ?: SecurityContextHolder.getContext().authentication
                        ?.let { SpringSecurityAuthenticationSupplier.toOidcUserInfoExtended(it) }
                    ?: throw IllegalArgumentException("No authenticated user")
            }
        }.getOrElse {
            Napier.w("${Paths.AuthorizeUrl} got error", it)
            throw it
        }
        Napier.d("${Paths.AuthorizeUrl} returns ${result.url}")
        request.logout()
        return if (request.getHeader(HttpHeaders.UserAgent)?.isSafariOniPhone() == true) {
            model["url"] = result.url
            ModelAndView("iphone-redirect")
        } else {
            result.toHttpResponse().toResponseEntity()
        }
    }

    /** Display a manual redirect back into the Wallet app, but only for iPhones */
    private fun String.isSafariOniPhone() = contains("Safari") && contains("iPhone")

    /**
     * Handles the token request sent by Wallets, see [SimpleAuthorizationService.tokenWithDpopNonce].
     */
    @PostMapping(Paths.TokenUrl, produces = [APPLICATION_JSON_VALUE])
    suspend fun token(
        @RequestBody requestBody: String,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        Napier.i("${Paths.TokenUrl} called")
        Napier.v("${Paths.TokenUrl} called with $requestBody")
        val params: TokenRequestParameters = requestBody.decodeFromFormUrlEncoded()
            ?: throw OAuth2Exception.InvalidRequest()
        val result = authorizationService.tokenWithDpopNonce(
            request = params,
            httpRequest = request.toRequestInfo()
                .also { Napier.v("${Paths.TokenUrl} called with $it") }
        ).getOrElse {
            Napier.w("${Paths.TokenUrl} got error", it)
            throw it
        }
        Napier.d("${Paths.TokenUrl} returns $result")
        return result.toHttpResponse().toResponseEntity()
    }

}
