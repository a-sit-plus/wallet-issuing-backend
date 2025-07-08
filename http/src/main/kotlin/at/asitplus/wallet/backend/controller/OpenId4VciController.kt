package at.asitplus.wallet.backend.controller

import at.asitplus.catching
import at.asitplus.catchingUnwrapped
import at.asitplus.openid.AuthenticationRequestParameters
import at.asitplus.openid.CredentialOffer
import at.asitplus.openid.CredentialRequestParameters
import at.asitplus.openid.IssuerMetadata
import at.asitplus.openid.JwtVcIssuerMetadata
import at.asitplus.openid.OAuth2AuthorizationServerMetadata
import at.asitplus.openid.OpenIdConstants
import at.asitplus.openid.TokenRequestParameters
import at.asitplus.wallet.backend.auth.SpringSecurityAuthenticationSupplier
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.config.EPrescriptionLoader
import at.asitplus.wallet.backend.data.OidcIssuerCredentialDataProvider
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.data.vckJsonSerializer
import at.asitplus.wallet.lib.oauth2.RequestInfo
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.OAuth2Error
import at.asitplus.wallet.lib.oidvci.decodeFromPostBody
import at.asitplus.wallet.lib.oidvci.decodeFromUrlQuery
import com.benasher44.uuid.uuid4
import io.github.aakira.napier.Napier
import io.ktor.http.*
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
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
import qrcode.QRCode


/**
 * Implements controller for OpenID 4 Verifiable Credential Issuance
 */
@RestController
class OpenId4VciController(
    private val issuer: Issuer,
    private val credentialIssuer: CredentialIssuer,
    private val authorizationService: SimpleAuthorizationService,
    private val backendConfigurationProperties: BackendConfigurationProperties,
    private val ePrescriptionLoader: EPrescriptionLoader,
) {

    private val mapNonceToOffer = mutableMapOf<String, CredentialOffer>()

    @GetMapping(OpenIdConstants.PATH_WELL_KNOWN_CREDENTIAL_ISSUER, produces = [APPLICATION_JSON_VALUE])
    fun issuerMetadata(): ResponseEntity<IssuerMetadata> {
        val metadata = credentialIssuer.metadata
        Napier.i("${OpenIdConstants.PATH_WELL_KNOWN_CREDENTIAL_ISSUER} returns $metadata")
        return ResponseEntity.ok(metadata)
    }

    @GetMapping(OpenIdConstants.PATH_WELL_KNOWN_OPENID_CONFIGURATION, produces = [APPLICATION_JSON_VALUE])
    fun oauthMetadata(): ResponseEntity<OAuth2AuthorizationServerMetadata> {
        val metadata = authorizationService.metadata
        Napier.i("${OpenIdConstants.PATH_WELL_KNOWN_OPENID_CONFIGURATION} returns $metadata")
        return ResponseEntity.ok(metadata)
    }

    @GetMapping(
        value = [OpenIdConstants.PATH_WELL_KNOWN_JWT_VC_ISSUER_METADATA,
            OpenIdConstants.PATH_WELL_KNOWN_JWT_VC_ISSUER_METADATA + "/*"
        ],
        produces = [APPLICATION_JSON_VALUE]
    )
    fun jwtVcMetadata(): ResponseEntity<JwtVcIssuerMetadata> {
        val metadata = credentialIssuer.jwtVcMetadata
        Napier.i("${OpenIdConstants.PATH_WELL_KNOWN_JWT_VC_ISSUER_METADATA} returns $metadata")
        return ResponseEntity.ok(metadata)
    }

    @GetMapping("/offer", produces = [APPLICATION_JSON_VALUE])
    fun offer(): ResponseEntity<CredentialOffer> = runBlocking {
        Napier.i("/offer called")
        val offer =
            authorizationService.credentialOfferWithAuthorizationCode(credentialIssuer.metadata.credentialIssuer)
        Napier.d("/offer returns $offer")
        return@runBlocking ResponseEntity.ok(offer)
    }

    @GetMapping("/offer/{nonce}", produces = [APPLICATION_JSON_VALUE])
    fun offerForNonce(@PathVariable nonce: String): ResponseEntity<CredentialOffer> = runBlocking {
        Napier.i("/offer/$nonce called")
        mapNonceToOffer[nonce]?.let {
            Napier.d("/offer/$nonce returns $it")
            ResponseEntity.ok(it)
        } ?: ResponseEntity.notFound().build()
    }

    @GetMapping("/")
    fun index(
        model: ModelMap,
        authentication: Authentication?
    ): ModelAndView = runBlocking {
        val authenticatedUser = SpringSecurityAuthenticationSupplier.toOidcUserInfoExtended(authentication)
        Napier.i("/index called with ${authenticatedUser?.userInfo?.subject}")
        authenticatedUser?.let {
            val offer = authorizationService.credentialOfferWithPreAuthnForUser(
                authenticatedUser,
                credentialIssuer.metadata.credentialIssuer
            )
            val nonce = uuid4().toString().also { mapNonceToOffer[it] = offer }
            val credentialOfferUrl = "${backendConfigurationProperties.publicContext}/offer/$nonce"
            val url = "openid-credential-offer://?credential_offer_uri=$credentialOfferUrl"
            Napier.d("/index sets credential offer URL $url")
            model["qrcode"] = QRCode.ofSquares().build(url).render().getBytes()
                .also { Napier.d("/index generates QR code with ${it.size} bytes") }
                .encodeToString(Base64())
        }
        ModelAndView("index")
    }

    @PostMapping("/par", produces = [APPLICATION_JSON_VALUE])
    fun par(
        @RequestBody requestBody: String,
        request: HttpServletRequest,
    ): ResponseEntity<*> = runBlocking {
        Napier.i("/par called")
        Napier.v("/par called with $requestBody")
        val params: AuthenticationRequestParameters = requestBody.decodeFromPostBody()
            ?: return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)
        val result = authorizationService.par(
            params,
            request.getHeader(O_AUTH_CLIENT_ATTESTATION),
            request.getHeader(O_AUTH_CLIENT_ATTESTATION_POP),
        ).getOrElse {
            Napier.w("/par got error", it)
            return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)
        }
        Napier.d("/par returns $result")
        return@runBlocking ResponseEntity
            .status(HttpStatus.CREATED)
            .body(vckJsonSerializer.encodeToString(result))
    }

    @PostMapping("/nonce", produces = [APPLICATION_JSON_VALUE])
    fun nonce(
    ): ResponseEntity<*> = runBlocking {
        Napier.i("/nonce called")
        val result = credentialIssuer.nonce().getOrElse {
            Napier.w("/nonce got error", it)
            return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)
        }
        Napier.d("/nonce returns $result")
        return@runBlocking ResponseEntity.ok(vckJsonSerializer.encodeToString(result))
    }

    /**
     * Logs out the user from the Spring Boot session, so that new requests need to be authorized again,
     * using the configured OAuth2 AS. Subsequent requests to [token] and [credential] are secured
     * by the authorization code returned here.
     */
    // TODO add "PreAuthorize" annotation?
    @RequestMapping("/authorize", method = [RequestMethod.POST, RequestMethod.GET])
    fun authorize(
        @RequestParam requestParams: Map<String, String>,
        @RequestBody requestBody: String?,
        request: HttpServletRequest,
        model: ModelMap,
        authentication: Authentication? = null,
    ) = runBlocking {
        Napier.i("/authorize called")
        Napier.v("/authorize called with $requestParams and $requestBody")
        val params: AuthenticationRequestParameters =
            if (requestBody.isNullOrEmpty()) requestParams.decodeFromUrlQuery()
            else requestBody.decodeFromPostBody()

        val result = authorizationService.authorize(params) {
            catching {
                SpringSecurityAuthenticationSupplier.toOidcUserInfoExtended(authentication)
                    ?: throw IllegalArgumentException("No authenticated user")
            }
        }.getOrElse {
            Napier.w("/authorize got error", it)
            return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)
        }
        Napier.d("/authorize returns ${result.url}")
        val userAgent = request.getHeader(HttpHeaders.USER_AGENT)
        return@runBlocking if (userAgent?.isSafariOniPhone() == true) {
            model["url"] = result.url
            ModelAndView("iphone-redirect")
        } else {
            buildOidcRedirect(result.url)
        }.also { request.logout() }
    }

    private fun String.isSafariOniPhone() = contains("Safari") && contains("iPhone")

    @PostMapping("/token", produces = [APPLICATION_JSON_VALUE])
    fun token(
        @RequestBody requestBody: String,
        request: HttpServletRequest,
    ): ResponseEntity<*> = runBlocking {
        Napier.i("/token called")
        Napier.v("/token called with $requestBody")
        val params: TokenRequestParameters = requestBody.decodeFromPostBody()
            ?: return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)
        val result = authorizationService.token(
            request = params,
            httpRequest = request.toRequestInfo()
        ).getOrElse {
            Napier.w("/token got error", it)
            return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)
        }
        Napier.d("/token returns $result")
        return@runBlocking ResponseEntity.ok(vckJsonSerializer.encodeToString(result))
    }

    private fun HttpServletRequest.toRequestInfo() = RequestInfo(
        url = requestURL.toString(),
        method = HttpMethod.parse(method),
        dpop = getHeader("DPoP"),
        clientAttestation = getHeader(O_AUTH_CLIENT_ATTESTATION),
        clientAttestationPop = getHeader(O_AUTH_CLIENT_ATTESTATION_POP),
    )

    @PostMapping("/credential", produces = [APPLICATION_JSON_VALUE])
    fun credential(
        @RequestBody requestBody: String,
        request: HttpServletRequest,
    ): ResponseEntity<*> = runBlocking {
        Napier.i("/credential called")
        val authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION)
        Napier.v("/credential called with $authorizationHeader and $requestBody")
        val params = catchingUnwrapped {
            vckJsonSerializer.decodeFromString<CredentialRequestParameters>(requestBody)
        }.getOrElse {
            Napier.w("/credential can't parse request", it)
            return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)
        }

        val credential = credentialIssuer.credential(
            authorizationHeader = authorizationHeader,
            params = params,
            request = request.toRequestInfo(),
            credentialDataProvider = OidcIssuerCredentialDataProvider(
                lifetime = backendConfigurationProperties.credentials.lifeTime,
                ePrescriptionLoader = ePrescriptionLoader
            ),
            issueCredential = {
                issuer.issueCredential(it)
            },
        ).getOrElse {
            Napier.w("/credential got error", it)
            return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)
        }
        Napier.d("/credential returns $credential")
        return@runBlocking ResponseEntity.ok(vckJsonSerializer.encodeToString(credential))
    }

    private fun buildOidcRedirect(location: String): ResponseEntity<String> = ResponseEntity
        .status(HttpStatus.FOUND)
        .header(HttpHeaders.LOCATION, location)
        .build()

    private fun buildOidcErrorResponse(error: String): ResponseEntity<OAuth2Error> = ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(OAuth2Error(error = error))

}


private const val O_AUTH_CLIENT_ATTESTATION = "OAuth-Client-Attestation"

private const val O_AUTH_CLIENT_ATTESTATION_POP = "OAuth-Client-Attestation-PoP"