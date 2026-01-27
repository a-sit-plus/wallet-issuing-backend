package at.asitplus.wallet.backend.controller

import at.asitplus.catching
import at.asitplus.openid.DisplayLogoProperties
import at.asitplus.openid.DisplayProperties
import at.asitplus.openid.IssuerMetadata
import at.asitplus.openid.JwtVcIssuerMetadata
import at.asitplus.openid.OAuth2AuthorizationServerMetadata
import at.asitplus.openid.OpenIdConstants
import at.asitplus.openid.OpenIdConstants.Errors
import at.asitplus.openid.RequestParameters
import at.asitplus.openid.TokenRequestParameters
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.auth.SpringSecurityAuthenticationSupplier
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.config.MetadataConfiguration
import at.asitplus.wallet.backend.data.OidcIssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.MediaTypes
import at.asitplus.wallet.lib.data.vckJsonSerializer
import at.asitplus.wallet.lib.ktor.openid.DPoP
import at.asitplus.wallet.lib.ktor.openid.DPoPNonce
import at.asitplus.wallet.lib.ktor.openid.OAuthClientAttestation
import at.asitplus.wallet.lib.ktor.openid.OAuthClientAttestationPop
import at.asitplus.wallet.lib.oauth2.RequestInfo
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.OAuth2Error
import at.asitplus.wallet.lib.oidvci.OAuth2Exception
import at.asitplus.wallet.lib.oidvci.WalletService
import at.asitplus.wallet.lib.oidvci.decodeFromPostBody
import at.asitplus.wallet.lib.oidvci.decodeFromUrlQuery
import io.github.aakira.napier.Napier
import io.ktor.client.utils.CacheControl
import io.ktor.http.*
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
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
import org.springframework.web.servlet.ModelAndView


/**
 * Implements controller for OpenID 4 Verifiable Credential Issuance
 */
@RestController
class OpenId4VciController(
    private val credentialIssuer: CredentialIssuer,
    private val authorizationService: SimpleAuthorizationService,
    private val backendConfigurationProperties: BackendConfigurationProperties,
) {

    @GetMapping(OpenIdConstants.PATH_WELL_KNOWN_CREDENTIAL_ISSUER, produces = [APPLICATION_JSON_VALUE])
    fun issuerMetadata(): ResponseEntity<IssuerMetadata> = run {
        val metadata = credentialIssuer.metadata.copy(
            displayProperties = setOf(
                backendConfigurationProperties.metadata.toDisplayProperties()
            )
        )
        Napier.i("${OpenIdConstants.PATH_WELL_KNOWN_CREDENTIAL_ISSUER} returns $metadata")
        ResponseEntity.ok(metadata)
    }

    private fun MetadataConfiguration.toDisplayProperties() = DisplayProperties(
        name = name,
        locale = "en-US",
        logo = DisplayLogoProperties(uri = logo)
    )

    @GetMapping(OpenIdConstants.PATH_WELL_KNOWN_OPENID_CONFIGURATION, produces = [APPLICATION_JSON_VALUE])
    fun openidMetadata(): ResponseEntity<OAuth2AuthorizationServerMetadata> = runBlocking {
        val metadata = authorizationService.metadata()
        Napier.i("${OpenIdConstants.PATH_WELL_KNOWN_OPENID_CONFIGURATION} returns $metadata")
        ResponseEntity.ok(metadata)
    }

    @GetMapping(OpenIdConstants.PATH_WELL_KNOWN_OAUTH_AUTHORIZATION_SERVER, produces = [APPLICATION_JSON_VALUE])
    fun oauthMetadata(): ResponseEntity<OAuth2AuthorizationServerMetadata> = runBlocking {
        val metadata = authorizationService.metadata()
        Napier.i("${OpenIdConstants.PATH_WELL_KNOWN_OAUTH_AUTHORIZATION_SERVER} returns $metadata")
        ResponseEntity.ok(metadata)
    }

    @GetMapping(
        value = [OpenIdConstants.PATH_WELL_KNOWN_JWT_VC_ISSUER_METADATA,
            OpenIdConstants.PATH_WELL_KNOWN_JWT_VC_ISSUER_METADATA + "/*"
        ],
        produces = [APPLICATION_JSON_VALUE]
    )
    fun jwtVcMetadata(): ResponseEntity<JwtVcIssuerMetadata> = run {
        val metadata = credentialIssuer.jwtVcMetadata
        Napier.i("${OpenIdConstants.PATH_WELL_KNOWN_JWT_VC_ISSUER_METADATA} returns $metadata")
        ResponseEntity.ok(metadata)
    }

    /**
     * Called by the Wallet when pushing an authorization request, see [SimpleAuthorizationService.par]
     */
    @PostMapping(Paths.ParUrl, produces = [APPLICATION_JSON_VALUE])
    fun par(
        @RequestBody requestBody: String,
        request: HttpServletRequest,
    ): ResponseEntity<*> = runBlocking {
        Napier.i("${Paths.ParUrl} called")
        Napier.v("${Paths.ParUrl} called with $requestBody")
        val params: RequestParameters = requestBody.decodeFromPostBody()
            ?: return@runBlocking buildOidcErrorResponse(OAuth2Exception.InvalidRequest())
        val result = authorizationService.parWithDpopNonce(
            request = params,
            httpRequest = request.toRequestInfo().also {
                Napier.v("${Paths.ParUrl} called with $it")
            },
        ).getOrElse {
            Napier.w("${Paths.ParUrl} got error", it)
            return@runBlocking buildOidcErrorResponse(it)
        }
        Napier.d("${Paths.ParUrl} returns $result")
        ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.CacheControl, CacheControl.NO_STORE)
            .header(HttpHeaders.DPoPNonce, result.dpopNonce)
            .body(vckJsonSerializer.encodeToString(result.response))
    }

    /**
     * Called by the Wallet to get a nonce for their proof-of-possessions, see [CredentialIssuer.nonceWithDpopNonce].
     */
    @PostMapping(Paths.NonceUrl, produces = [APPLICATION_JSON_VALUE])
    fun nonce(
    ): ResponseEntity<*> = runBlocking {
        Napier.i("${Paths.NonceUrl} called")
        val result = credentialIssuer.nonceWithDpopNonce().getOrElse {
            Napier.w("${Paths.NonceUrl} got error", it)
            return@runBlocking buildOidcErrorResponse(it)
        }
        Napier.d("${Paths.NonceUrl} returns $result")
        ResponseEntity.status(HttpStatus.OK)
            .header(HttpHeaders.CacheControl, CacheControl.NO_STORE)
            .header(HttpHeaders.DPoPNonce, result.dpopNonce)
            .body(vckJsonSerializer.encodeToString(result.response))
    }

    /**
     * Logs out the user from the Spring Boot session, so that new requests need to be authorized again,
     * using the configured OAuth2 AS. Subsequent requests to [token] and [credential] are secured
     * by the authorization code returned here.
     * See [SimpleAuthorizationService.authorize].
     */
    // TODO add "PreAuthorize" annotation?
    @RequestMapping(Paths.AuthorizeUrl, method = [RequestMethod.POST, RequestMethod.GET])
    fun authorize(
        @RequestParam requestParams: Map<String, String>,
        @RequestBody requestBody: String?,
        request: HttpServletRequest,
        model: ModelMap,
        authentication: Authentication? = null,
    ) = runBlocking {
        Napier.i("${Paths.AuthorizeUrl} called")
        Napier.v("${Paths.AuthorizeUrl} called with $requestParams and $requestBody")
        val params: RequestParameters =
            if (requestBody.isNullOrEmpty()) requestParams.decodeFromUrlQuery()
            else requestBody.decodeFromPostBody()

        val result = authorizationService.authorize(params) {
            catching {
                SpringSecurityAuthenticationSupplier.toOidcUserInfoExtended(authentication)
                    ?: SecurityContextHolder.getContext().authentication
                        ?.let { SpringSecurityAuthenticationSupplier.toOidcUserInfoExtended(it) }
                    ?: throw IllegalArgumentException("No authenticated user")
            }
        }.getOrElse {
            Napier.w("${Paths.AuthorizeUrl} got error", it)
            return@runBlocking buildOidcErrorResponse(it)
        }
        Napier.d("${Paths.AuthorizeUrl} returns ${result.url}")
        val userAgent = request.getHeader(HttpHeaders.UserAgent)
        if (userAgent?.isSafariOniPhone() == true) {
            model["url"] = result.url
            ModelAndView("iphone-redirect")
        } else {
            buildOidcRedirect(result.url)
        }.also { request.logout() }
    }

    /** Display a manual redirect back into the Wallet app, but only for iPhones */
    private fun String.isSafariOniPhone() = contains("Safari") && contains("iPhone")

    /**
     * Handles the token request sent by Wallets, see [SimpleAuthorizationService.tokenWithDpopNonce].
     */
    @PostMapping(Paths.TokenUrl, produces = [APPLICATION_JSON_VALUE])
    fun token(
        @RequestBody requestBody: String,
        request: HttpServletRequest,
    ): ResponseEntity<*> = runBlocking {
        Napier.i("${Paths.TokenUrl} called")
        Napier.v("${Paths.TokenUrl} called with $requestBody")
        val params: TokenRequestParameters = requestBody.decodeFromPostBody()
            ?: return@runBlocking buildOidcErrorResponse(OAuth2Exception.InvalidRequest())
        val result = authorizationService.tokenWithDpopNonce(
            request = params,
            httpRequest = request.toRequestInfo()
                .also { Napier.v("${Paths.TokenUrl} called with $it") }
        ).getOrElse {
            Napier.w("${Paths.TokenUrl} got error", it)
            return@runBlocking buildOidcErrorResponse(it)
        }
        Napier.d("${Paths.TokenUrl} returns $result")
        ResponseEntity.status(HttpStatus.OK)
            .header(HttpHeaders.CacheControl, CacheControl.NO_STORE)
            .header(HttpHeaders.DPoPNonce, result.dpopNonce)
            .body(vckJsonSerializer.encodeToString(result.response))
    }

    private fun HttpServletRequest.toRequestInfo() = RequestInfo(
        url = requestURL.toString(),
        method = HttpMethod.parse(method),
        dpop = getHeader(HttpHeaders.DPoP),
        clientAttestation = getHeader(HttpHeaders.OAuthClientAttestation),
        clientAttestationPop = getHeader(HttpHeaders.OAuthClientAttestationPop),
    )

    /**
     * Issues the credential, when the token sent by the Wallet is valid,
     * see [CredentialIssuer.credential].
     */
    @PostMapping(Paths.CredentialUrl, produces = [APPLICATION_JSON_VALUE])
    fun credential(
        @RequestBody requestBody: String,
        request: HttpServletRequest,
    ): ResponseEntity<*> = runBlocking {
        Napier.i("${Paths.CredentialUrl} called")
        val authorizationHeader = request.getHeader(HttpHeaders.Authorization)
        Napier.v("${Paths.CredentialUrl} called with $authorizationHeader and $requestBody")
        val params = WalletService.CredentialRequest.parse(requestBody).getOrElse {
            Napier.w("${Paths.CredentialUrl} can't parse request", it)
            return@runBlocking buildOidcErrorResponse(it)
        }
        val credential = credentialIssuer.credential(
            authorizationHeader = authorizationHeader,
            params = params,
            request = request.toRequestInfo().also {
                Napier.v("${Paths.CredentialUrl} called with $it")
            },
            credentialDataProvider = OidcIssuerCredentialDataProvider(
                lifetime = backendConfigurationProperties.credentials.lifeTime,
            ),
        ).getOrElse {
            Napier.w("${Paths.CredentialUrl} got error", it)
            return@runBlocking buildOidcErrorResponse(it)
        }
        Napier.d("${Paths.CredentialUrl} returns $credential")
        credential.toResponseEntity()
    }

    private suspend fun CredentialIssuer.CredentialResponse.toResponseEntity(): ResponseEntity<String?> =
        when (this) {
            is CredentialIssuer.CredentialResponse.Encrypted -> this.toResponseEntity()
            is CredentialIssuer.CredentialResponse.Plain -> this.toResponseEntity()
        }

    private suspend fun CredentialIssuer.CredentialResponse.Plain.toResponseEntity(): ResponseEntity<String?> =
        ResponseEntity.status(HttpStatus.OK)
            .header(HttpHeaders.DPoPNonce, authorizationService.getDpopNonce())
            .header(HttpHeaders.ContentType, MediaTypes.Application.JSON)
            .body(vckJsonSerializer.encodeToString(response))

    private suspend fun CredentialIssuer.CredentialResponse.Encrypted.toResponseEntity(): ResponseEntity<String?> =
        ResponseEntity
            .status(HttpStatus.OK)
            .header(HttpHeaders.DPoPNonce, authorizationService.getDpopNonce())
            .header(HttpHeaders.ContentType, MediaTypes.Application.JWT)
            .body(vckJsonSerializer.encodeToString(response.serialize()))

    private fun buildOidcRedirect(location: String): ResponseEntity<String> = ResponseEntity
        .status(HttpStatus.FOUND)
        .header(HttpHeaders.Location, location)
        .build()

    private fun buildOidcErrorResponse(throwable: Throwable): ResponseEntity<OAuth2Error> =
        when (throwable) {
            is OAuth2Exception.UseDpopNonce -> ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .header(HttpHeaders.DPoPNonce, throwable.dpopNonce)
                .body(throwable.toOAuth2Error())

            is OAuth2Exception -> ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(throwable.toOAuth2Error())

            else -> ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(OAuth2Error(error = Errors.INVALID_REQUEST))
        }

}


