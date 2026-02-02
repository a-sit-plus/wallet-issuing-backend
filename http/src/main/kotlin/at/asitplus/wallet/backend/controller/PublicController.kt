package at.asitplus.wallet.backend.controller

import at.asitplus.openid.JarRequestParameters
import at.asitplus.openid.JwtVcIssuerMetadata
import at.asitplus.openid.OpenIdConstants
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JwsSigned
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.backend.Extensions.appendPath
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtScheme
import at.asitplus.wallet.lib.RequestOptionsCredential
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.Validator
import at.asitplus.wallet.lib.agent.ValidatorMdoc
import at.asitplus.wallet.lib.agent.ValidatorSdJwt
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.agent.validation.TokenStatusResolverImpl
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.StatusListJwt
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.MediaTypes
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListTokenPayload
import at.asitplus.wallet.lib.jws.JwsContentTypeConstants
import at.asitplus.wallet.lib.jws.VerifyJwsObject
import at.asitplus.wallet.lib.oauth2.OAuth2Utils
import at.asitplus.wallet.lib.oidvci.encodeToParameters
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.openid.ClientIdScheme
import at.asitplus.wallet.lib.openid.OpenId4VpRequestOptions
import at.asitplus.wallet.lib.openid.OpenId4VpVerifier
import at.asitplus.wallet.lib.utils.DefaultMapStore
import at.asitplus.wallet.lib.utils.MapStore
import com.benasher44.uuid.uuid4
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import jakarta.servlet.http.HttpSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.tomcat.websocket.AuthenticationException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

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
) {
    private val transactionIdToSessionIdMap: MapStore<String, String> = DefaultMapStore(lifetime = 4.hours)

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(joseCompliantSerializer)
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Napier.i(message = message, tag = "at.asitplus.http")
                }
            }
            level = LogLevel.ALL
        }
    }

    val clientIdScheme = runBlocking {
        ClientIdScheme.CertificateHash(
            chain = listOf(verifierKeyMaterial.getCertificate()!!),
            redirectUri = configurationProperties.publicContext.toString(),
        )
    }

    fun validator() = Validator(
        tokenStatusResolver = TokenStatusResolverImpl(
            resolveStatusListToken = {
                Napier.i("Resolving token status for from $it")
                run {
                    httpClient.get(it.string) {
                        header(HttpHeaders.Accept, MediaTypes.Application.STATUSLIST_JWT)
                    }.body<String>()
                }.let {
                    JwsSigned.deserialize<StatusListTokenPayload>(StatusListTokenPayload.serializer(), it).getOrThrow()
                }.let {
                    StatusListJwt(it, Clock.System.now())
                }
            },
        ),
    )

    val openIdVerifier = OpenId4VpVerifier(
        keyMaterial = verifierKeyMaterial,
        clientIdScheme = clientIdScheme,
        verifier = VerifierAgent(
            identifier = clientIdScheme.clientId,
            validatorSdJwt = ValidatorSdJwt(
                verifyJwsObject = VerifyJwsObject(
                    publicKeyLookup = {
                        (it.payload as? JsonObject)?.get("iss")?.jsonPrimitive?.content?.let { iss ->
                            val url = OAuth2Utils.insertWellKnownPath(iss, OpenIdConstants.WellKnownPaths.JwtVcIssuer)
                            Napier.i("Resolving Key for $iss from $url")
                            httpClient.get(url).body<JwtVcIssuerMetadata>().jsonWebKeySet?.keys?.toSet<JsonWebKey>()
                        }
                    }
                ),
                validator = validator(),
            ),
            validatorMdoc = ValidatorMdoc(
                validator = validator(),
            )
        )
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
    fun login(
        model: ModelMap,
        request: HttpServletRequest,
        @RequestParam("error", required = false) error: String? = null,
    ) = runBlocking {
        model["oauthUrls"] = clientRegistrations?.map {
            OAuth2ClientRegistration(it.clientName, it.loginUrl())
        }
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
        ModelAndView("login", model)
    }

    @Serializable
    data class StatusResponse(val authenticated: Boolean, val redirectUrl: String?)

    /**
     * Will be called async. from `login.html` to redirect the user to the front page,
     * once the authentication with the EU PID is completed.
     */
    @GetMapping(Paths.LoginStatusUrl)
    fun status(
        request: HttpServletRequest,
        response: HttpServletResponse,
        session: HttpSession,
    ) = runBlocking {
        val user = session.getAttribute(SESSION_KEY_OPENID4VP_USER) as? OpenId4VpUser?
            ?: return@runBlocking StatusResponse(false, null)

        Napier.i("${Paths.LoginStatusUrl} got successful authentication in session ${session.id}: $user")
        val targetUrl = setAuthenticationInSession(user, session, request, response)
        val redirectUrl = if (targetUrl.isNotEmpty()) targetUrl.toString() else "/"
        StatusResponse(true, redirectUrl)
    }

    /**
     * Will be called from the Wallet when the user scans the QR Code to login with an EU PID.
     */
    @GetMapping("${Paths.Transaction.GetUrl}/{transactionId}")
    @ResponseBody
    fun transactionGet(
        @PathVariable transactionId: String,
    ): ResponseEntity<String> = runBlocking {
        Napier.i("${Paths.Transaction.GetUrl}/$transactionId called")
        if (transactionIdToSessionIdMap.get(transactionId) == null)
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
                .also { Napier.w("${Paths.Transaction.GetUrl}/$transactionId returns NOT_FOUND") }

        try {
            val responseUrl = configurationProperties.publicContext
                .appendPath(Paths.Transaction.ResultUrl + "/" + transactionId)
            val state = uuid4().toString()
            val result = openIdVerifier.createAuthnRequestAsSignedRequestObject(
                OpenId4VpRequestOptions(
                    state = state,
                    responseMode = OpenIdConstants.ResponseMode.DirectPost,
                    responseUrl = responseUrl,
                    credentials = setOf(
                        requestPidSdJwt()
                    ),
                )
            ).getOrThrow().serialize()
                .also { Napier.i("${Paths.Transaction.GetUrl}/$transactionId returns $it") }
            ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/" + JwsContentTypeConstants.OAUTH_AUTHZ_REQUEST))
                .body(result)
        } catch (e: Exception) {
            Napier.w("${Paths.Transaction.GetUrl}/$transactionId error", e)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.localizedMessage)
        }
    }

    private fun requestPidSdJwt(): RequestOptionsCredential = RequestOptionsCredential(
        credentialScheme = EuPidSdJwtScheme,
        representation = ConstantIndex.CredentialRepresentation.SD_JWT,
        requestedAttributes = setOf(
            EuPidSdJwtScheme.SdJwtAttributes.FAMILY_NAME,
            EuPidSdJwtScheme.SdJwtAttributes.GIVEN_NAME,
            EuPidSdJwtScheme.SdJwtAttributes.BIRTH_DATE
        ),
    )

    /**
     * Will be called from the Wallet when the user logs in with the EU PID.
     */
    @PostMapping("${Paths.Transaction.ResultUrl}/{id}")
    fun transactionPost(
        @PathVariable id: String,
        @RequestBody requestBody: String,
    ): ResponseEntity<Void> = runBlocking {
        Napier.i("${Paths.Transaction.ResultUrl}/$id called with $requestBody")
        val desktopSessionId = transactionIdToSessionIdMap.remove(id)
        if (desktopSessionId == null) {
            Napier.w("${Paths.Transaction.ResultUrl}/$id returns NOT_FOUND")
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        val user = try {
            validateOpenId4VpResponse(requestBody, openIdVerifier)
        } catch (e: Throwable) {
            Napier.w("${Paths.Transaction.ResultUrl}/$id error", e)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.localizedMessage, e)
        }
        val session = sessionRepository.findById(desktopSessionId)
        Napier.i("${Paths.Transaction.ResultUrl}/$id is updating session ${session.id}")
        session.setAttribute(SESSION_KEY_OPENID4VP_USER, user)
        sessionRepository.save(session)
        ResponseEntity.ok().build()
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

    private suspend fun validateOpenId4VpResponse(
        requestBody: String,
        verifier: OpenId4VpVerifier,
    ): OpenId4VpUser? = when (val result = verifier.validateAuthnResponse(requestBody)) {
        is AuthnResponseResult.VerifiableDCQLPresentationValidationResults -> result.toOpenId4VpUser()
        is AuthnResponseResult.SuccessSdJwt -> result.toOpenId4VpUser()
        is AuthnResponseResult.SuccessIso -> result.toOpenId4VpUser()
        is AuthnResponseResult.VerifiablePresentationValidationResults -> result.toOpenId4VpUser()
        is AuthnResponseResult.Success -> throw RuntimeException("Plain JWT Success not expected")
        is AuthnResponseResult.Error -> throw RuntimeException(result.reason, result.cause)
        is AuthnResponseResult.ValidationError -> throw RuntimeException("Failed: ${result.field}", result.cause)
        is AuthnResponseResult.IdToken -> throw RuntimeException("Only got id_token")
    }

    private fun HttpSession.getAuthnException(): String? =
        (getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION) as? AuthenticationException)?.message

    private fun ClientRegistration.loginUrl(): String =
        "${OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI}/${registrationId}"

}
