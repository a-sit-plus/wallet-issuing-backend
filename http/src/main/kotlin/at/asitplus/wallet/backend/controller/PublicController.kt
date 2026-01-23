package at.asitplus.wallet.backend.controller

import at.asitplus.openid.JarRequestParameters
import at.asitplus.openid.OpenIdConstants
import at.asitplus.wallet.backend.Extensions.sha256
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.config.RevocationListConfigurationProperties
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtScheme
import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.agent.KeyStoreMaterial
import at.asitplus.wallet.lib.agent.StatusListIssuer
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.MediaTypes
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListAggregation
import at.asitplus.wallet.lib.jws.JwsContentTypeConstants
import at.asitplus.wallet.lib.oidvci.encodeToParameters
import at.asitplus.wallet.lib.openid.AuthnResponseResult
import at.asitplus.wallet.lib.openid.ClientIdScheme
import at.asitplus.wallet.lib.openid.OpenId4VpVerifier
import at.asitplus.wallet.lib.openid.RequestOptions
import at.asitplus.wallet.lib.openid.RequestOptionsCredential
import com.benasher44.uuid.uuid4
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import jakarta.servlet.http.HttpSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.apache.tomcat.websocket.AuthenticationException
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
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
import org.springframework.web.context.request.WebRequest
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import org.springframework.web.util.UriComponentsBuilder
import qrcode.QRCode
import java.io.File
import java.nio.file.Path
import java.security.KeyStore
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.readBytes
import kotlin.time.toJavaDuration

private const val SESSION_KEY_OPENID4VP_USER = "sessionOpenId4VpResponse"

/**
 * Public endpoints, available without authentication:
 * - Revocation list for Verifiable Credentials (RevocationList2020)
 */
@RestController
class PublicController(
    private val statusListIssuer: StatusListIssuer,
    private val configurationProperties: BackendConfigurationProperties,
    private val clientRegistrations: InMemoryClientRegistrationRepository?,
    private val successHandler: AuthenticationSuccessHandler,
    private val transactionIdToSessionIdMap: NonceToSessionMap,
    private val sessionRepository: MapSessionRepository,
) {
    // TODO configuration!
    private val verifierKeyMaterial = File("verifier.p12").run {
        if (exists()) {
            KeyStoreMaterial(
                keyStore = KeyStore.getInstance("PKCS12").apply {
                    load(inputStream(), "changeit".toCharArray())
                },
                keyAlias = "verifier",
                privateKeyPassword = "changeit".toCharArray(),
                certAlias = "verifier",
            )
        } else {
            EphemeralKeyWithSelfSignedCert()
        }
    }
    val clientIdScheme = runBlocking {
        ClientIdScheme.CertificateHash(
            chain = listOf(verifierKeyMaterial.getCertificate()!!),
            redirectUri = configurationProperties.publicContext,
        )
    }

    val openIdVerifier = OpenId4VpVerifier(
        keyMaterial = verifierKeyMaterial,
        clientIdScheme = clientIdScheme,
    )

    fun buildQrCodeUrl(requestUrl: String) =
        ServletUriComponentsBuilder.fromUriString("haip-vp://").apply {
            JarRequestParameters(
                clientId = clientIdScheme.clientId,
                requestUri = requestUrl,
            ).encodeToParameters()
                .forEach { queryParam(it.key, it.value) }
        }.toUriString()

    @GetMapping("/credentials/status/current", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getStatutsListAggregation(): ResponseEntity<StatusListAggregation> = runBlocking {
        Napier.i("/credentials/status/current called")
        val rl = statusListIssuer.provideStatusListAggregation()
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
            model["loginError"] = request.getSession(false).getAuthnException()?.ifEmpty { null }
                ?: "Invalid credentials"
        }

        val transactionId = uuid4().toString()
            .also { transactionIdToSessionIdMap.put(it, request.getSession(true).id) }
        val transactionUrl = UriComponentsBuilder.fromUriString(configurationProperties.publicContext)
            .pathSegment("transaction")
            .pathSegment("get")
            .pathSegment(transactionId).build().toUriString()
        val qrCodeUrl = buildQrCodeUrl(transactionUrl)
        model["loginPidUrl"] = qrCodeUrl
        model["loginPidQrCode"] = QRCode.ofSquares().build(qrCodeUrl).render().getBytes()
            .encodeToString(Base64())
        return ModelAndView("login", model)
    }

    @Serializable
    data class StatusResponse(val authenticated: Boolean, val redirectUrl: String?)

    @GetMapping("/status")
    fun status(
        request: HttpServletRequest,
        response: HttpServletResponse,
        session: HttpSession,
    ) = runBlocking {
        val user = session.getAttribute(SESSION_KEY_OPENID4VP_USER) as? OpenId4VpUser?
            ?: return@runBlocking StatusResponse(false, null)

        Napier.i("/status got successful authentication in session ${session.id}: $user")
        val targetUrl = setAuthenticationInSession(user, session, request, response)
        val redirectUrl = if (targetUrl.isNotEmpty()) targetUrl.toString() else "/"
        StatusResponse(true, redirectUrl)
    }

    @GetMapping("/transaction/get/{transactionId}")
    @ResponseBody
    fun transactionGet(
        @PathVariable transactionId: String,
    ): ResponseEntity<String> = runBlocking {
        Napier.i("/transaction/get/$transactionId called")
        if (transactionIdToSessionIdMap.get(transactionId) == null)
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
                .also { Napier.w("/transaction/get/$transactionId returns NOT_FOUND") }

        try {
            val responseUrl = UriComponentsBuilder.fromUriString(configurationProperties.publicContext)
                .pathSegment("transaction")
                .pathSegment("result")
                .pathSegment(transactionId).build().toUriString()
            val state = uuid4().toString()
            val result = openIdVerifier.createAuthnRequestAsSignedRequestObject(
                RequestOptions(
                    state = state,
                    responseMode = OpenIdConstants.ResponseMode.DirectPost,
                    responseUrl = responseUrl,
                    credentials = setOf(
                        RequestOptionsCredential(
                            credentialScheme = EuPidSdJwtScheme,
                            representation = ConstantIndex.CredentialRepresentation.SD_JWT,
                            requestedAttributes = setOf(
                                EuPidSdJwtScheme.SdJwtAttributes.FAMILY_NAME,
                                EuPidSdJwtScheme.SdJwtAttributes.GIVEN_NAME,
                                EuPidSdJwtScheme.SdJwtAttributes.BIRTH_DATE
                            ),
                        )
                    ),
                )
            ).getOrThrow().serialize()
                .also { Napier.i("/transaction/get/$transactionId returns $it") }
            ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/" + JwsContentTypeConstants.OAUTH_AUTHZ_REQUEST))
                .body(result)
        } catch (e: Exception) {
            Napier.w("/transaction/get/$transactionId error", e)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.localizedMessage)
        }
    }

    /**
     * Expects SIOPv2 authn response as request body,
     * called from Wallet App upon answering authn request from [transactionGet].
     */
    @PostMapping("/transaction/result/{id}")
    fun transactionPost(
        @PathVariable id: String,
        @RequestBody requestBody: String,
    ): ResponseEntity<Void> = runBlocking {
        Napier.i("/transaction/result/$id called with $requestBody")
        val desktopSessionId = transactionIdToSessionIdMap.remove(id)
        if (desktopSessionId == null) {
            Napier.w("/transaction/result/$id returns NOT_FOUND")
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        val user = try {
            validateOpenId4VpResponse(requestBody, openIdVerifier)
        } catch (e: Throwable) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.localizedMessage, e)
        }
        val session = sessionRepository.findById(desktopSessionId)
        Napier.i("/transaction/result/$id is updating session ${session.id}")
        session.setAttribute(SESSION_KEY_OPENID4VP_USER, user)
        sessionRepository.save(session)
        ResponseEntity.ok().build<Void>()
    }

    private fun setAuthenticationInSession(
        user: OpenId4VpUser,
        session: HttpSession,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): StringBuilder {
        val authentication = UsernamePasswordAuthenticationToken(user, null, listOf(GrantedAuthority { "ROLE_OPENID4VP" }))
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
        is AuthnResponseResult.VerifiableDCQLPresentationValidationResults -> result.validationResults.toOpenId4VpUser()
        is AuthnResponseResult.Success -> throw RuntimeException("Plain JWT Success not expected")
        is AuthnResponseResult.SuccessSdJwt -> result.toApiItemCredential().toOpenId4VpUser()
        is AuthnResponseResult.SuccessIso -> result.toApiItem().toOpenId4VpUser()
        is AuthnResponseResult.Error -> throw RuntimeException(result.reason, result.cause)
        is AuthnResponseResult.ValidationError -> throw RuntimeException("Failed: ${result.field}", result.cause)
        is AuthnResponseResult.VerifiablePresentationValidationResults -> result.toApiItem().toOpenId4VpUser()
        is AuthnResponseResult.IdToken -> throw RuntimeException("Only got id_token")
    }

    private fun HttpSession.getAuthnException(): String? =
        (getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION) as? AuthenticationException)?.message

    private fun ClientRegistration.loginUrl(): String =
        "${OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI}/${registrationId}"

    private val statusListJwtType = MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_JWT)
    private val statusListCwtType = MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_CWT)

    @GetMapping("/credentials/status/{timePeriod}")
    fun getVcRevocationList(@PathVariable timePeriod: Int, request: WebRequest): ResponseEntity<*> {
        Napier.i("/credentials/status/$timePeriod called")
        val acceptMediaTypes = request.getHeader(HttpHeaders.ACCEPT)?.let { MediaType.parseMediaTypes(it) }
        val contentType = acceptMediaTypes.toStatusListContentType()
        val path = configurationProperties.revocationList.getPath(acceptMediaTypes, timePeriod)
        val content = if (path.exists() && path.isReadable()) path.readBytes() else null
        return if (content == null || content.isEmpty()) {
            Napier.w("/credentials/status/$timePeriod returns HTTP 404")
            ResponseEntity.notFound().build<String>()
        } else {
            val etag = content.sha256().encodeToString(Base16()).uppercase()
            val cacheControl =
                CacheControl.maxAge(configurationProperties.revocationList.regularWriteTimeoutDuration.toJavaDuration())
            // Spring (or Tomcat?) appends "-gzip" to the ETag set by us, so we'll need to check that variant too
            val lastModified = path.toFile().lastModified()
            if (request.checkNotModified(etag, lastModified)
                || request.checkNotModified("${etag}-gzip", lastModified)
            ) {
                Napier.d("/credentials/status/$timePeriod returns HTTP 304")
                ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(cacheControl)
                    .contentType(contentType)
                    .build<String>()
            } else {
                Napier.i("/credentials/status/$timePeriod returns ${content.count()} chars")
                ResponseEntity.ok()
                    .cacheControl(cacheControl)
                    .contentType(contentType)
                    .body(content)
            }
        }
    }

    private fun RevocationListConfigurationProperties.getPath(types: List<MediaType>?, timePeriod: Int): Path =
        if (types.isCompatibleWithCwt())
            Path(cwtPath, timePeriod.toString())
        else
            Path(jwtPath, timePeriod.toString())

    private fun List<MediaType>?.toStatusListContentType(): MediaType =
        if (isCompatibleWithCwt()) statusListCwtType else
            if (isCompatibleWithJwt()) statusListJwtType else
                MediaType.TEXT_PLAIN

    private fun List<MediaType>?.isCompatibleWithCwt() =
        this?.any { it.isCompatibleWith(statusListCwtType) } == true

    private fun List<MediaType>?.isCompatibleWithJwt() =
        this?.any { it.isCompatibleWith(statusListJwtType) } == true

}
