package at.asitplus.wallet.backend.controller

import at.asitplus.openid.JarRequestParameters
import at.asitplus.openid.OpenIdConstants
import at.asitplus.openid.dcql.DCQLClaimsPathPointer
import at.asitplus.openid.encodeToParameters
import at.asitplus.wallet.backend.Extensions.appendPath
import at.asitplus.wallet.backend.Extensions.toResponseEntity
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtDataElements.BIRTH_DATE
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtDataElements.FAMILY_NAME
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtDataElements.GIVEN_NAME
import at.asitplus.wallet.lib.RequestOptionsCredential
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.openid.ClientIdScheme
import at.asitplus.wallet.lib.openid.CreationOptions
import at.asitplus.wallet.lib.openid.CredentialPresentationRequestBuilder
import at.asitplus.wallet.lib.openid.OpenId4VpRequestOptions
import at.asitplus.wallet.lib.openid.OpenId4VpVerifier
import at.asitplus.wallet.lib.openid.directPostHttpResponse
import at.asitplus.wallet.lib.openid.loadRequestObjectHttpResponse
import at.asitplus.wallet.lib.utils.DefaultMapStore
import at.asitplus.wallet.lib.utils.MapStore
import at.asitplus.wallet.lib.validation.CredentialValidator
import at.asitplus.wallet.lib.validation.ValidationPolicy
import com.benasher44.uuid.uuid4
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import jakarta.servlet.http.HttpSession
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.apache.tomcat.websocket.AuthenticationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter
import org.springframework.security.web.WebAttributes
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
import org.springframework.session.MapSessionRepository
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import qrcode.QRCode

private const val SESSION_KEY_OPENID4VP_USER = "sessionOpenId4VpResponse"

/**
 * Handles login of the user, either through OIDC or with an EU PID
 */
@RestController
class PublicController(
    private val configurationProperties: BackendConfigurationProperties,
    private val clientRegistrations: InMemoryClientRegistrationRepository?,
    private val successHandler: AuthenticationSuccessHandler,
    private val sessionRepository: MapSessionRepository,
    private val verifierKeyMaterial: KeyMaterial,
    private val pidCredentialValidator: CredentialValidator,
    private val pidPresentationPolicy: ValidationPolicy,
) {
    private val transactionIdToSessionIdMap: MapStore<String, String> = DefaultMapStore(lifetime = 4.hours)

    val clientIdScheme = runBlocking {
        ClientIdScheme.CertificateHash(
            chain = listOf(verifierKeyMaterial.getCertificate()!!),
            redirectUri = configurationProperties.publicContext.toString(),
        )
    }

    val openIdVerifier = OpenId4VpVerifier(
        keyMaterial = verifierKeyMaterial,
        clientIdScheme = clientIdScheme,
        credentialValidator = pidCredentialValidator,
        presentationPolicy = pidPresentationPolicy,
    )

    fun buildQrCodeUrl(requestUrl: String): String =
        ServletUriComponentsBuilder.fromUriString(Paths.Schemes.HaipVp + "://").apply {
            JarRequestParameters(
                clientId = clientIdScheme.clientId,
                requestUri = requestUrl,
            ).encodeToParameters()
                .forEach { queryParam(it.key, it.value) }
        }.toUriString()

    data class OAuth2ClientRegistration(
        val name: String, val url: String,
    )

    /**
     * Displays configured OAuth2 client registrations and an QR Code to login with an EU PID
     */
    @RequestMapping(Paths.LoginUrl)
    suspend fun login(
        model: ModelMap,
        request: HttpServletRequest,
        @RequestParam("error", required = false) error: String? = null,
    ): ModelAndView {
        clientRegistrations?.map {
            OAuth2ClientRegistration(it.clientName, it.loginUrl())
        }?.let { model["oauthUrls"] = it }
        if (error != null) {
            // from DefaultLoginPageGeneratingFilter
            model["loginError"] = request.getSession(false).getAuthnException()?.ifEmpty { null }
                ?: "Invalid credentials"
        }

        val transactionId = uuid4().toString()
            .also { transactionIdToSessionIdMap.put(it, request.getSession(true).id) }
        val transactionUrl = configurationProperties.publicContext
            .appendPath(Paths.Transaction.GetUrl + "/" + transactionId)
        val qrCodeUrl = buildQrCodeUrl(transactionUrl)
        model["loginPidUrl"] = qrCodeUrl
        model["loginPidQrCode"] = QRCode.ofSquares().build(qrCodeUrl).render().getBytes()
            .encodeToString(Base64())
        return ModelAndView("login", model)
    }

    @Serializable
    data class StatusResponse(val authenticated: Boolean, val redirectUrl: String?, val error: String? = null)

    /**
     * Will be called async. from `login.html` to redirect the user to the front page,
     * once the authentication with the EU PID is completed.
     */
    @GetMapping(Paths.LoginStatusUrl)
    suspend fun status(
        request: HttpServletRequest,
        response: HttpServletResponse,
        session: HttpSession,
    ): StatusResponse {
        val user = session.getAttribute(SESSION_KEY_OPENID4VP_USER) as? OpenId4VpUser?
            ?: return StatusResponse(false, null)

        if (!user.isAccepted) return StatusResponse(false, null, user.presentationError ?: "PID presentation rejected")

        Napier.i("${Paths.LoginStatusUrl} got successful authentication in session ${session.id}: $user")
        val targetUrl = setAuthenticationInSession(user, session, request, response)
        val redirectUrl = if (targetUrl.isNotEmpty()) targetUrl.toString() else "/"
        return StatusResponse(true, redirectUrl)
    }

    /**
     * Will be called from the Wallet when the user scans the QR Code to login with an EU PID.
     */
    @GetMapping("${Paths.Transaction.GetUrl}/{transactionId}")
    @ResponseBody
    suspend fun transactionGet(
        @PathVariable transactionId: String,
    ): ResponseEntity<String> {
        Napier.i("${Paths.Transaction.GetUrl}/$transactionId called")
        if (transactionIdToSessionIdMap.get(transactionId) == null)
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
                .also { Napier.w("${Paths.Transaction.GetUrl}/$transactionId returns NOT_FOUND") }

        val responseUrl = configurationProperties.publicContext
            .appendPath(Paths.Transaction.ResultUrl + "/" + transactionId)
        val state = uuid4().toString()
        val requestUrl = configurationProperties.publicContext
            .appendPath(Paths.Transaction.GetUrl + "/" + transactionId)
        val createdRequest = openIdVerifier.createAuthnRequest(
            OpenId4VpRequestOptions(
                state = state,
                responseMode = OpenIdConstants.ResponseMode.DirectPost,
                responseUrl = responseUrl,
                presentationRequest = CredentialPresentationRequestBuilder(requestPidSdJwt()).toDCQLRequest()
            ),
            CreationOptions.SignedRequestByReference(
                walletUrl = "${Paths.Schemes.HaipVp}://",
                requestUrl = requestUrl,
            ),
        ).getOrElse {
            Napier.w("${Paths.Transaction.GetUrl}/$transactionId error", it)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.localizedMessage, it)
        }
        val result = createdRequest.loadRequestObjectHttpResponse(null)
            .getOrElse {
                Napier.w("${Paths.Transaction.GetUrl}/$transactionId error", it)
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.localizedMessage, it)
            }
            .also { Napier.i("${Paths.Transaction.GetUrl}/$transactionId returns $it") }
        return result.toResponseEntity()
    }

    private fun requestPidSdJwt() = listOf(
        RequestOptionsCredential(
            // resolved from remote type metadata, registered at boot
            credentialScheme = AttributeIndex.resolveSdJwtAttributeType("urn:eudi:pid:1")
                ?: error("EU PID SD-JWT scheme not resolved"),
            representation = ConstantIndex.CredentialRepresentation.SD_JWT,
            attributePaths = setOf(FAMILY_NAME, GIVEN_NAME, BIRTH_DATE).map { DCQLClaimsPathPointer(it) }.toSet(),
        )
    )

    /**
     * Will be called from the Wallet when the user logs in with the EU PID.
     */
    @PostMapping("${Paths.Transaction.ResultUrl}/{id}")
    suspend fun transactionPost(
        @PathVariable id: String,
        @RequestBody requestBody: String,
    ): ResponseEntity<String> {
        Napier.i("${Paths.Transaction.ResultUrl}/$id called with $requestBody")
        val desktopSessionId = transactionIdToSessionIdMap.remove(id)
        if (desktopSessionId == null) {
            Napier.w("${Paths.Transaction.ResultUrl}/$id returns NOT_FOUND")
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        val result = openIdVerifier.validateAuthnResponse(requestBody)
            .getOrElse {
                Napier.w("${Paths.Transaction.ResultUrl}/$id error", it)
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.localizedMessage, it)
            }
        val expectedResponseUrl = configurationProperties.publicContext.appendPath(Paths.Transaction.ResultUrl + "/" + id)
        if (result.request.responseUrl != expectedResponseUrl) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Presentation answers another login transaction")
        }
        val user = result.toUser()
        sessionRepository.findById(desktopSessionId)?.let { session ->
            Napier.i("${Paths.Transaction.ResultUrl}/$id is updating session ${session.id}")
            session.setAttribute(SESSION_KEY_OPENID4VP_USER, user)
            sessionRepository.save(session)
        }
        return directPostHttpResponse().toResponseEntity()
    }

    private fun setAuthenticationInSession(
        user: OpenId4VpUser,
        session: HttpSession,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): StringBuilder {
        val authentication =
            UsernamePasswordAuthenticationToken(user, null, listOf(GrantedAuthority { "ROLE_OPENID4VP" }))
        val targetUrl = StringBuilder()
        val urlCapturingResponse = object : HttpServletResponseWrapper(response) {
            override fun sendRedirect(url: String) {
                targetUrl.append(url)
            }
        }
        successHandler.onAuthenticationSuccess(request, urlCapturingResponse, authentication)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, context)
        HttpSessionSecurityContextRepository().saveContext(context, request, response)
        return targetUrl
    }

    private fun HttpSession.getAuthnException(): String? =
        (getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION) as? AuthenticationException)?.message

    private fun ClientRegistration.loginUrl(): String =
        "${OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI}/${registrationId}"

}
