package at.asitplus.wallet.backend.controller

import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.data.CredentialDataProvider
import at.asitplus.wallet.backend.data.EidasCredentialDataProvider
import at.asitplus.wallet.backend.service.IssueCredentialAdapter
import at.asitplus.wallet.lib.agent.NextMessage
import at.asitplus.wallet.lib.encodeBase64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
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
 */
@Profile("eidasid")
@RestController
class EidasIdController(
    private val issueCredentialAdapter: IssueCredentialAdapter,
    private val extNonceAuthnService: ExtNonceAuthnService,
    private val configurationProperties: BackendConfigurationProperties,
    private val credentialDataProvider: CredentialDataProvider,
    private val clock: Clock
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

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
    @PreAuthorize("hasAuthority(\"DEVICE_BINDING\")")
    fun issueCredential(
        @RequestBody body: String,
        authentication: Authentication,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        log.info("/eidasid/issue called for {} with '{}'", authentication, body)
        return when (val result = issueCredentialAdapter.parseMessage(body)) {
            is NextMessage.Result<*> -> ResponseEntity.ok().build<String>()
                .also { log.info("/eidasid/issue returns HTTP 200: Finished") }
            is NextMessage.Send -> ResponseEntity.ok(result.message)
                .also {
                    log.info(
                        "/eidasid/issue returns HTTP 200: {}...",
                        result.message.take(128)
                    )
                }
            is NextMessage.Error -> ResponseEntity.status(HttpStatus.BAD_REQUEST).build<String>()
                .also { log.warn("/eidasid/issue returns HTTP 400: Incorrect protocol state") }
            is NextMessage.SendProblemReport -> ResponseEntity.ok(result.message)
                .also {
                    log.info(
                        "/eidasid/issue returns HTTP 200: Problem Report {}",
                        result.message
                    )
                }
            is NextMessage.ReceivedProblemReport -> ResponseEntity.ok().build<String>()
                .also {
                    log.info(
                        "/eidasid/issue returns HTTP 200: Received Problem Report {}",
                        result.message
                    )
                }
            else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build<String>()
                .also { log.warn("/eidasid/issue returns HTTP 500: Internal error {}", result) }
        }.also { request.logout() }
    }

    /**
     * Displays a QR code to scan with the Wallet App to get a nonce for authn during the device binding process
     */
    @GetMapping("/eidasid/initialize")
    @PreAuthorize("hasAuthority(\"EIDASID\")")
    fun initialize(model: ModelMap): ModelAndView {
        log.info("/eidasid/initialize called")
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
        log.info("Storing EIDAS claims for '{}': {}", nonceBpk.bpk, eidasClaim)
        credentialDataProvider.storeClaims(eidasClaim, nonceBpk.bpk)

        val content = UriComponentsBuilder.fromHttpUrl(configurationProperties.publicContext)
            .pathSegment("help", "wallet")
            .fragment("nonce=${nonceBpk.nonce}&server=${configurationProperties.publicContext}")
            .toUriString()
        val qrCodeImage = createQrCodeImage(content, configurationProperties.debug.qrCodeSize)
        model["qrcode"] = qrCodeImage.encodeBase64()
        model["qrcodeWidth"] = configurationProperties.debug.qrCodeSize
        model["creation"] = clock.now().toString()
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