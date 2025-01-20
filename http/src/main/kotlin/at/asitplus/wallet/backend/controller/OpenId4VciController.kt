package at.asitplus.wallet.backend.controller

import at.asitplus.openid.*
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.lib.data.vckJsonSerializer
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.OAuth2Error
import at.asitplus.wallet.lib.oidvci.decodeFromPostBody
import at.asitplus.wallet.lib.oidvci.decodeFromUrlQuery
import com.benasher44.uuid.uuid4
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.ModelAndView
import qrcode.QRCode

/**
 * Implements controller for OpenID 4 Verifiable Credential Issuance
 */
@RestController
class OpenId4VciController(
    private val credentialIssuer: CredentialIssuer,
    private val authorizationService: SimpleAuthorizationService,
    private val backendConfigurationProperties: BackendConfigurationProperties,
    private val authenticationSupplier: AuthenticationSupplier,
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
        val offer = credentialIssuer.credentialOfferWithAuthorizationCode()
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

    @GetMapping("/", produces = [APPLICATION_JSON_VALUE])
    fun index(model: ModelMap): ModelAndView = runBlocking {
        val principal = authenticationSupplier.getCurrentUserOidcDetails()
        Napier.i("/index called with $principal")
        principal?.let {
            val offer = credentialIssuer.credentialOfferWithPreAuthnForUser(principal)
            val nonce = uuid4().toString().also { mapNonceToOffer[it] = offer }
            val credentialOfferUrl = "${backendConfigurationProperties.publicContext}/offer/$nonce"
            val url = "oid4vci://wallet.a-sit.at/offer?credential_offer_uri=$credentialOfferUrl"
            Napier.d("/index sets credential offer URL $url")
            model["qrcode"] = QRCode.ofSquares().build(url).render().getBytes()
                .also { Napier.d("/index generates QR code with ${it.size} bytes") }
                .encodeToString(Base64())
        }
        ModelAndView("index")
    }

    /**
     * Logs out the user from the Spring Boot session, so that new requests need to be authorized again,
     * using the configured OAuth2 AS. Subsequent requests to [token] and [credential] are secured
     * by the authorization code returned here.
     */
    @RequestMapping("/authorize", method = [RequestMethod.POST, RequestMethod.GET])
    fun authorize(
        @RequestParam requestParams: Map<String, String>,
        @RequestBody requestBody: String?,
        request: HttpServletRequest,
        model: ModelMap,
    ) = runBlocking {
        Napier.i("/authorize called")
        Napier.v("/authorize called with $requestParams and $requestBody")
        val params: AuthenticationRequestParameters =
            if (requestBody.isNullOrEmpty()) requestParams.decodeFromUrlQuery()
            else requestBody.decodeFromPostBody()
        val result = authorizationService.authorize(params).getOrElse {
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
    fun token(@RequestBody requestBody: String): ResponseEntity<*> = runBlocking {
        Napier.i("/token called")
        Napier.v("/token called with $requestBody")
        val params: TokenRequestParameters = requestBody.decodeFromPostBody()
            ?: return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)
        val result = authorizationService.token(params).getOrElse {
            Napier.w("/token got error", it)
            return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)
        }
        Napier.d("/token returns $result")
        return@runBlocking ResponseEntity.ok(vckJsonSerializer.encodeToString(result))
    }

    @PostMapping("/credential", produces = [APPLICATION_JSON_VALUE])
    fun credential(
        @RequestBody requestBody: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorizationHeader: String,
    ): ResponseEntity<*> = runBlocking {
        Napier.i("/credential called")
        Napier.v("/credential called with $authorizationHeader and $requestBody")
        val params = CredentialRequestParameters.deserialize(requestBody).getOrElse {
            Napier.w("/credential can't parse request", it)
            return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)
        }
        val accessToken = authorizationHeader.removePrefix("bearer ").removePrefix("Bearer ")
        val credential = credentialIssuer.credential(accessToken, params).getOrElse {
            Napier.w("/credential got error", it)
            return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)
        }
        Napier.d("/credential returns $credential")
        return@runBlocking ResponseEntity.ok(vckJsonSerializer.encodeToString(credential))
    }

    private fun buildOidcRedirect(location: String): ResponseEntity<String> {
        return ResponseEntity
            .status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, location)
            .build()
    }

    private fun buildOidcErrorResponse(error: String): ResponseEntity<OAuth2Error> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(OAuth2Error(error = error))
    }

}

