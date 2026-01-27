package at.asitplus.wallet.backend.controller

import at.asitplus.catching
import at.asitplus.openid.CredentialFormatEnum.DC_SD_JWT
import at.asitplus.openid.CredentialOffer
import at.asitplus.openid.DisplayLogoProperties
import at.asitplus.openid.DisplayProperties
import at.asitplus.openid.IssuerMetadata
import at.asitplus.openid.JwtVcIssuerMetadata
import at.asitplus.openid.OAuth2AuthorizationServerMetadata
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.openid.OpenIdConstants
import at.asitplus.openid.OpenIdConstants.Errors
import at.asitplus.openid.RequestParameters
import at.asitplus.openid.TokenRequestParameters
import at.asitplus.wallet.ageverification.AgeVerificationScheme
import at.asitplus.wallet.backend.Extensions.appendPath
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.auth.SpringSecurityAuthenticationSupplier
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.config.MetadataConfiguration
import at.asitplus.wallet.backend.data.OidcIssuerCredentialDataProvider
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtScheme
import at.asitplus.wallet.lib.data.MediaTypes
import at.asitplus.wallet.lib.data.vckJsonSerializer
import at.asitplus.wallet.lib.ktor.openid.DPoP
import at.asitplus.wallet.lib.ktor.openid.DPoPNonce
import at.asitplus.wallet.lib.ktor.openid.OAuthClientAttestation
import at.asitplus.wallet.lib.ktor.openid.OAuthClientAttestationPop
import at.asitplus.wallet.lib.oauth2.RequestInfo
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.DefaultCredentialSchemeMapper
import at.asitplus.wallet.lib.oidvci.DefaultMapStore
import at.asitplus.wallet.lib.oidvci.MapStore
import at.asitplus.wallet.lib.oidvci.OAuth2Error
import at.asitplus.wallet.lib.oidvci.OAuth2Exception
import at.asitplus.wallet.lib.oidvci.WalletService
import at.asitplus.wallet.lib.oidvci.decodeFromPostBody
import at.asitplus.wallet.lib.oidvci.decodeFromUrlQuery
import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
import com.benasher44.uuid.uuid4
import io.github.aakira.napier.Napier
import io.ktor.client.utils.CacheControl
import io.ktor.http.*
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.util.UriComponentsBuilder
import qrcode.QRCode
import kotlin.time.Duration.Companion.hours


/**
 * Implements controller for OpenID 4 Verifiable Credential Issuance
 */
@RestController
class OpenId4VciController(
    private val credentialIssuer: CredentialIssuer,
    private val authorizationService: SimpleAuthorizationService,
    private val backendConfigurationProperties: BackendConfigurationProperties,
) {

    private val nonceToOfferMap: MapStore<String, CredentialOffer> = DefaultMapStore(lifetime = 4.hours)

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

    @GetMapping("${Paths.OfferUrl}/{nonce}", produces = [APPLICATION_JSON_VALUE])
    fun offerForNonce(@PathVariable nonce: String): ResponseEntity<CredentialOffer> = runBlocking {
        Napier.i("${Paths.OfferUrl}/$nonce called")
        nonceToOfferMap.get(nonce)?.let {
            Napier.d("${Paths.OfferUrl}/$nonce returns $it")
            ResponseEntity.ok(it)
        } ?: ResponseEntity.notFound().build()
    }

    @GetMapping("/")
    fun index(
        model: ModelMap,
        session: HttpSession,
        authentication: Authentication?,
    ): ModelAndView = runBlocking {
        Napier.i("/index called with session ${session.id} and $authentication")
        val user = SpringSecurityAuthenticationSupplier.toOidcUserInfoExtended(authentication)
            ?: SecurityContextHolder.getContext().authentication
                ?.let { SpringSecurityAuthenticationSupplier.toOidcUserInfoExtended(it) }
        Napier.i("/index called with ${user?.userInfo?.subject}")
        model["tabs"] = listOfNotNull(
            buildTabItemAuthCode(
                title = "All-Code",
                description = "All credentials with auth code",
                configurationIds = listOf(),
                urlScheme = Paths.Schemes.HaipVci
            ),
            buildTabItemAuthCode(
                title = "PID-SD-JWT-Code",
                description = "PID in SD-JWT with auth code",
                configurationId = DefaultCredentialSchemeMapper()
                    .encodeToCredentialIdentifier(EuPidSdJwtScheme.sdJwtType, DC_SD_JWT),
                urlScheme = Paths.Schemes.HaipVci
            ),
            buildTabItemAuthCode(
                title = "PID-MDOC-Code",
                description = "PID in ISO MDOC with auth code",
                configurationId = EuPidScheme.isoNamespace,
                urlScheme = Paths.Schemes.HaipVci
            ),
            buildTabItemAuthCode(
                title = "MDL-MDOC-Code",
                description = "mDL in ISO MDOC with auth code",
                configurationId = MobileDrivingLicenceScheme.isoNamespace,
                urlScheme = Paths.Schemes.HaipVci
            ),
            buildTabItemAuthCode(
                title = "AV-MDOC-Code",
                description = "Age Verification in ISO MDOC with auth code",
                configurationId = AgeVerificationScheme.isoNamespace,
                urlScheme = Paths.Schemes.Av
            ),
            user?.let {
                buildTabItemPreAuthn(
                    user = user,
                    title = "All-pre",
                    description = "All credentials with pre-authn",
                    configurationIds = listOf(),
                    urlScheme = Paths.Schemes.HaipVci
                )
            },
            user?.let {
                buildTabItemPreAuthn(
                    user = user,
                    title = "PID-SD-JWT-pre",
                    description = "PID in SD-JWT with pre-authn",
                    configurationId = DefaultCredentialSchemeMapper()
                        .encodeToCredentialIdentifier(EuPidSdJwtScheme.sdJwtType, DC_SD_JWT),
                    urlScheme = Paths.Schemes.HaipVci
                )
            },
            user?.let {
                buildTabItemPreAuthn(
                    user = user,
                    title = "PID-MDOC-pre",
                    description = "PID in ISO MDOC with pre-authn",
                    configurationId = EuPidScheme.isoNamespace,
                    urlScheme = Paths.Schemes.HaipVci
                )
            },
            user?.let {
                buildTabItemPreAuthn(
                    user = user,
                    title = "MDL-MDOC-pre",
                    description = "mDL in ISO MDOC with pre-authn",
                    configurationId = MobileDrivingLicenceScheme.isoNamespace,
                    urlScheme = Paths.Schemes.HaipVci
                )
            },
            user?.let {
                buildTabItemPreAuthn(
                    user = user,
                    title = "AV-MDOC-pre",
                    description = "Age Verification in ISO MDOC with pre-authn",
                    configurationId = AgeVerificationScheme.isoNamespace,
                    urlScheme = Paths.Schemes.Av
                )
            },
        )

        ModelAndView("index")
    }

    private suspend fun buildTabItemPreAuthn(
        user: OidcUserInfoExtended,
        title: String,
        description: String,
        configurationId: String,
        urlScheme: String,
    ) = buildTabItemPreAuthn(user, title, description, listOf(configurationId), urlScheme)

    private suspend fun buildTabItemPreAuthn(
        user: OidcUserInfoExtended,
        title: String,
        description: String,
        configurationIds: Collection<String>,
        urlScheme: String,
    ): TabItem = run {
        val offer = authorizationService.credentialOfferWithPreAuthnForUser(
            user = user,
            credentialIssuer = credentialIssuer.metadata.credentialIssuer,
            configurationIds = configurationIds
        )
        val nonce = uuid4().toString().also { nonceToOfferMap.put(it, offer) }
        val credentialOfferUrl = backendConfigurationProperties.publicContext.appendPath(Paths.OfferUrl + "/" + nonce)
        val url = UriComponentsBuilder.newInstance()
            .scheme(urlScheme).queryParam(Paths.QueryParams.CredentialOfferUri, credentialOfferUrl)
            .toUriString()
        val qrBase64 = QRCode.ofSquares().build(url).render().getBytes().encodeToString(Base64())
        TabItem(nonce, title, description, qrBase64)
    }

    private suspend fun buildTabItemAuthCode(
        title: String,
        description: String,
        configurationId: String,
        urlScheme: String,
    ): TabItem = buildTabItemAuthCode(title, description, setOf(configurationId), urlScheme)

    private suspend fun buildTabItemAuthCode(
        title: String,
        description: String,
        configurationIds: Collection<String>,
        urlScheme: String,
    ): TabItem = run {
        val offer = authorizationService.credentialOfferWithAuthorizationCode(
            credentialIssuer = credentialIssuer.metadata.credentialIssuer,
            configurationIds = configurationIds
        )
        val nonce = uuid4().toString().also { nonceToOfferMap.put(it, offer) }
        val credentialOfferUrl = backendConfigurationProperties.publicContext.appendPath(Paths.OfferUrl + "/" + nonce)
        val url = UriComponentsBuilder.newInstance()
            .scheme(urlScheme).queryParam(Paths.QueryParams.CredentialOfferUri, credentialOfferUrl)
            .toUriString()
        val qrBase64 = QRCode.ofSquares().build(url).render().getBytes().encodeToString(Base64())
        TabItem(nonce, title, description, qrBase64)
    }

    data class TabItem(
        val id: String,
        val title: String,
        val description: String,
        val qrBase64: String,
    )

    @PostMapping(Paths.ParUrl, produces = [APPLICATION_JSON_VALUE])
    fun par(
        @RequestBody requestBody: String,
        request: HttpServletRequest,
    ): ResponseEntity<*> = runBlocking {
        Napier.i("${Paths.ParUrl} called")
        Napier.v("${Paths.ParUrl} called with $requestBody")
        val params: RequestParameters = requestBody.decodeFromPostBody()
            ?: return@runBlocking buildOidcErrorResponse(OAuth2Exception.InvalidRequest())
        val result = authorizationService.par(
            params,
            request.toRequestInfo().also {
                Napier.v("${Paths.ParUrl} called with $it")
            },
        ).getOrElse {
            return@runBlocking buildOidcErrorResponse(it)
                .also { Napier.w("${Paths.ParUrl} sends error $it") }
        }
        Napier.d("${Paths.ParUrl} returns $result")
        ResponseEntity
            .status(HttpStatus.CREATED)
            .header(HttpHeaders.DPoPNonce, authorizationService.getDpopNonce())
            .body(vckJsonSerializer.encodeToString(result))
    }

    @PostMapping(Paths.NonceUrl, produces = [APPLICATION_JSON_VALUE])
    fun nonce(
    ): ResponseEntity<*> = runBlocking {
        Napier.i("${Paths.NonceUrl} called")
        val result = credentialIssuer.nonceWithDpopNonce().getOrElse {
            return@runBlocking buildOidcErrorResponse(it)
                .also { Napier.w("${Paths.NonceUrl} sends error $it") }
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

    private fun String.isSafariOniPhone() = contains("Safari") && contains("iPhone")

    @PostMapping(Paths.TokenUrl, produces = [APPLICATION_JSON_VALUE])
    fun token(
        @RequestBody requestBody: String,
        request: HttpServletRequest,
    ): ResponseEntity<*> = runBlocking {
        Napier.i("${Paths.TokenUrl} called")
        Napier.v("${Paths.TokenUrl} called with $requestBody")
        val params: TokenRequestParameters = requestBody.decodeFromPostBody()
            ?: return@runBlocking buildOidcErrorResponse(OAuth2Exception.InvalidRequest())
        val result = authorizationService.token(
            request = params,
            httpRequest = request.toRequestInfo()
                .also { Napier.v("${Paths.TokenUrl} called with $it") }
        ).getOrElse {
            Napier.w("${Paths.TokenUrl} got error", it)
            return@runBlocking buildOidcErrorResponse(it)
        }
        Napier.d("${Paths.TokenUrl} returns $result")
        ResponseEntity
            .status(HttpStatus.OK)
            .header(HttpHeaders.DPoPNonce, authorizationService.getDpopNonce())
            .body(vckJsonSerializer.encodeToString(result))
    }

    private fun HttpServletRequest.toRequestInfo() = RequestInfo(
        url = requestURL.toString(),
        method = HttpMethod.parse(method),
        dpop = getHeader(HttpHeaders.DPoP),
        clientAttestation = getHeader(HttpHeaders.OAuthClientAttestation),
        clientAttestationPop = getHeader(HttpHeaders.OAuthClientAttestationPop),
    )

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
            return@runBlocking buildOidcErrorResponse(it)
                .also { Napier.w("${Paths.CredentialUrl} sends error $it") }
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


