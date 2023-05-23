package at.asitplus.wallet.backend.controller

import at.asitplus.wallet.backend.ProfileConstants
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.backend.auth.WebSecurityConstants.AUTHORITY_DEVICE_BINDING
import at.asitplus.wallet.backend.auth.WebSecurityConstants.AUTHORITY_OIDC_EIDASID
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.data.CredentialDataProvider
import at.asitplus.wallet.backend.data.EidasCredentialDataProvider
import at.asitplus.wallet.backend.service.IssueCredentialAdapter
import at.asitplus.wallet.lib.aries.NextMessage
import at.asitplus.wallet.lib.oidvci.AuthorizationRequestParameters
import at.asitplus.wallet.lib.oidvci.CredentialRequestParameters
import at.asitplus.wallet.lib.oidvci.IssuerMetadata
import at.asitplus.wallet.lib.oidvci.IssuerService
import at.asitplus.wallet.lib.oidvci.OAuth2Error
import at.asitplus.wallet.lib.oidvci.OAuth2Exception
import at.asitplus.wallet.lib.oidvci.TokenRequestParameters
import at.asitplus.wallet.lib.oidvci.decodeFromPostBody
import at.asitplus.wallet.lib.oidvci.decodeFromUrlQuery
import at.asitplus.wallet.lib.oidvci.jsonSerializer
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.github.aakira.napier.Napier
import io.matthewnelson.component.base64.encodeBase64
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.util.UriComponentsBuilder
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO
import javax.servlet.http.HttpServletRequest

/**
 * Provides endpoints in the EIDAS deployment:
 * - REST for Wallet App to get credentials (with a device binding)
 * - MVC for Browser to display QR Code to initialize Wallet App
 * - OID4VCI for issuing credentials without a device binding
 */
@Profile(ProfileConstants.EIDASID)
@RestController
class EidasIdController(
    private val issueCredentialAdapter: IssueCredentialAdapter,
    private val extNonceAuthnService: ExtNonceAuthnService,
    private val configurationProperties: BackendConfigurationProperties,
    private val credentialDataProvider: CredentialDataProvider,
    private val issuerService: IssuerService,
) {

    @Operation(
        summary = "Issue credentials",
        description = "Issues a fresh instance of an EidasId to the Wallet app.",
        security = [SecurityRequirement(name = "deviceBinding")],
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "RequestCredential message of the IssueCredential protocol between Wallet and Issuer",
            content = [Content(examples = [ExampleObject(value = "<DIDcomm signed message>")])]
        ),
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "IssueCredential message of the IssueCredential protocol between Wallet and Issuer",
                content = [Content(examples = [ExampleObject(value = "<DIDcomm signed message>")])]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Incorrect protocol state, i.e. this message was not expected on protocol level",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Client is not authenticated, i.e. needs to answer challenge from header `WWW-Authenticate` first",
                content = [Content(examples = [ExampleObject(value = "")])]
            ),
            ApiResponse(responseCode = "500", ref = "errorResponse"),
        ]
    )
    @PostMapping("/eidasid/issue")
    @PreAuthorize("hasAuthority(\"$AUTHORITY_DEVICE_BINDING\")")
    fun issueCredential(
        @RequestBody body: String,
        authentication: Authentication,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        Napier.i("/eidasid/issue called")
        Napier.v("/eidasid/issue called for $authentication with '$body'")
        return when (val result = issueCredentialAdapter.parseMessage(body)) {
            is NextMessage.Result<*> -> ResponseEntity.ok().build<String>()
                .also { Napier.i("/eidasid/issue returns HTTP 200: Finished") }
            is NextMessage.Send -> ResponseEntity.ok(result.message)
                .also {
                    // TODO: No idea what data is sent around here... For now I treat it as critical
                    Napier.i("/eidasid/issue returns HTTP 200")
                    Napier.v("message: ${result.message.take(128)}...")
                }
            is NextMessage.Error -> ResponseEntity.status(HttpStatus.BAD_REQUEST).build<String>()
                .also { Napier.w("/eidasid/issue returns HTTP 400: Incorrect protocol state") }
            is NextMessage.SendProblemReport -> ResponseEntity.ok(result.message)
                .also {
                    Napier.i("/eidasid/issue returns HTTP 200: Problem Report")
                    Napier.v("Problem report: ${result.message}")
                }
            is NextMessage.ReceivedProblemReport -> ResponseEntity.ok().build<String>()
                .also {
                    Napier.i("/eidasid/issue returns HTTP 200: Received Problem Report")
                    Napier.v("Received Problem Report ${result.message}")
                }
        }.also { request.logout() }
    }

    @GetMapping("/.well-known/openid-credential-issuer")
    fun metadata(): ResponseEntity<IssuerMetadata> {
        val metadata = issuerService.metadata()
        Napier.i("/.well-known/openid-credential-issuer returns $metadata")
        return ResponseEntity.ok(metadata)
    }

    @RequestMapping("/authorize", method = [RequestMethod.POST, RequestMethod.GET])
    fun authorize(
        @RequestParam requestParams: Map<String, String>,
        @RequestBody requestBody: String?
    ): ResponseEntity<String> {
        Napier.i("/authorize called")
        Napier.v("/authorize called with $requestParams and $requestBody")
        val params: AuthorizationRequestParameters =
            if (requestBody.isNullOrEmpty()) requestParams.decodeFromUrlQuery()
            else requestBody.decodeFromPostBody()
        val location = issuerService.authorize(params)
        Napier.d("/authorize returns $location")
        return buildOidcRedirect(location)
    }

    @RequestMapping("/token", method = [RequestMethod.POST])
    fun token(@RequestBody requestBody: String): ResponseEntity<*> {
        Napier.i("/token called")
        Napier.v("/token called with $requestBody")
        val params: TokenRequestParameters = requestBody.decodeFromPostBody()
            ?: return buildOidcErrorResponse("invalid_request")
        return try {
            val result = issuerService.token(params)
            Napier.d("/token returns $result")
            ResponseEntity.ok(Json.encodeToString(result))
        } catch (e: OAuth2Exception) {
            Napier.w("/token error $e, $e.error")
            buildOidcErrorResponse(e.error)
        }
    }

    @RequestMapping("/credential", method = [RequestMethod.POST])
    fun credential(
        @RequestBody requestBody: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorizationHeader: String
    ): ResponseEntity<*> = runBlocking {
        Napier.i("/credential called")
        Napier.v("/credential called with $authorizationHeader and $requestBody")
        return@runBlocking suspendingCredential(authorizationHeader, requestBody)
    }

    private suspend fun suspendingCredential(
        authorizationHeader: String,
        requestBody: String
    ): ResponseEntity<out Any> {
        val params: CredentialRequestParameters = jsonSerializer.decodeFromString(requestBody)
            ?: return buildOidcErrorResponse("invalid_request")
        return try {
            val credential = issuerService.credential(authorizationHeader, params)
            Napier.d("/credential returns $credential")
            ResponseEntity.ok(jsonSerializer.encodeToString(credential))
        } catch (e: OAuth2Exception) {
            Napier.w("/credential error $e, $e.error")
            buildOidcErrorResponse(e.error)
        }
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

    /**
     * Displays a QR code to scan with the Wallet App to get a nonce for authn during the device binding process
     */
    @GetMapping("/eidasid/initialize")
    @PreAuthorize("hasAuthority(\"$AUTHORITY_OIDC_EIDASID\")")
    fun initialize(model: ModelMap): ModelAndView {
        Napier.i("/eidasid/initialize called")
        val nonceBpk = extNonceAuthnService.generateNonce()
        if (nonceBpk == null) {
            model["error"] = "Internal error: Could not generate nonce"
            return ModelAndView("initialize", model)
        }
        if (credentialDataProvider !is EidasCredentialDataProvider) {
            model["error"] = "Internal error: Configuration mismatch"
            return ModelAndView("initialize", model)
        }
        val principal = SecurityContextHolder.getContext()?.authentication?.principal
        if (principal !is OAuth2AuthenticatedPrincipal) {
            model["error"] = "Please login first"
            return ModelAndView("initialize", model)
        }
        val subject = principal.getAttribute<String>("sub")!! // "ZP:Bysw9ZBchD2iWuNu2taXqk3aK+I="
        val birthdate = principal.getAttribute<String>("birthdate")!! // "1990-01-01"
        val givenName = principal.getAttribute<String>("given_name")!! // "XXXGerda"
        val familyName =
            principal.getAttribute<String>("family_name")!! // "XXXMusterfrau Erwachsen"
        val eidasClaim =
            EidasCredentialDataProvider.EidasClaim(subject, birthdate, givenName, familyName)
        Napier.i("Storing EIDAS claims")
        Napier.v("Storing EIDAS claims for '${nonceBpk.bpk}': $eidasClaim")
        credentialDataProvider.storeClaims(eidasClaim, nonceBpk.bpk)

        val content = UriComponentsBuilder.fromHttpUrl(configurationProperties.publicContext)
            .pathSegment("help", "wallet")
            .fragment("nonce=${nonceBpk.nonce}&server=${configurationProperties.publicContext}")
            .toUriString()
        val qrCodeImage = createQrCodeImage(content, configurationProperties.debug.qrCodeSize)
        model["qrcode"] = qrCodeImage.encodeBase64()
        model["qrcodeWidth"] = configurationProperties.debug.qrCodeSize
        model["creation"] = Clock.System.now().toString()
        return ModelAndView("initialize", model)
    }

    private fun createQrCodeImage(content: String, size: Int): ByteArray {
        val options = Collections.singletonMap(EncodeHintType.MARGIN, 0)
        val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, options)
        val image = BufferedImage(bits.width, bits.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until bits.height) {
            for (x in 0 until bits.width) {
                image.setRGB(x, y, if (bits[x, y]) 0 else 0xffffff)
            }
        }
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, "png", outputStream)
        return outputStream.toByteArray()
    }

}