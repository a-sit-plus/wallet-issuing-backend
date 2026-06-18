package at.asitplus.wallet.backend.controller

import at.asitplus.dcapi.issuance.CredentialCreationOptions
import at.asitplus.openid.CredentialOffer
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.wallet.backend.Extensions.appendPath
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.auth.SpringSecurityAuthenticationSupplier
import at.asitplus.wallet.backend.config.AV_DOCTYPE
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.config.CredentialOffering
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.ISO_MDOC
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.PLAIN_JWT
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.SD_JWT
import at.asitplus.wallet.lib.data.CredentialScheme
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.utils.DefaultMapStore
import at.asitplus.wallet.lib.utils.MapStore
import com.benasher44.uuid.uuid4
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import jakarta.servlet.http.HttpSession
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.util.UriComponentsBuilder
import qrcode.QRCode
import kotlin.time.Duration.Companion.hours


/**
 * Implements controller for OpenID 4 Verifiable Credential Issuance
 */
@RestController
class IndexController(
    private val credentialIssuer: CredentialIssuer,
    private val authorizationService: SimpleAuthorizationService,
    private val backendConfigurationProperties: BackendConfigurationProperties,
    private val credentialOfferings: List<CredentialOffering>,
) {

    private val nonceToOfferMap: MapStore<String, CredentialOffer> = DefaultMapStore(lifetime = 4.hours)

    /**
     * Will be called by the Wallet when loading an offer that is presented as a QR Code on the index page
     */
    @GetMapping("${Paths.OfferUrl}/{nonce}", produces = [APPLICATION_JSON_VALUE])
    suspend fun offerForNonce(@PathVariable nonce: String): CredentialOffer {
        Napier.i("${Paths.OfferUrl}/$nonce called")
        return nonceToOfferMap.get(nonce)
            ?.also { Napier.d("${Paths.OfferUrl}/$nonce returns $it") }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }

    /**
     * DC API issuance payload derived from the credential offer, returned as JSON.
     */
    @GetMapping("${Paths.DcApiCreateRequestUrl}/{nonce}", produces = [APPLICATION_JSON_VALUE])
    suspend fun dcApiCreateRequest(@PathVariable nonce: String): CredentialCreationOptions {
        Napier.i("${Paths.DcApiCreateRequestUrl}/$nonce called")
        val offer = nonceToOfferMap.get(nonce) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        require(offer.grants?.authorizationCode?.authorizationServer == null)
        val enrichedOffer = offer.copy(
            grants = offer.grants?.copy(
                preAuthorizedCode = offer.grants?.preAuthorizedCode?.copy(
                    authorizationServer = null
                )
            ),
            authorizationServerMetadata = authorizationService.metadata(),
            credentialIssuerMetadata = credentialIssuer.metadata.copy(authorizationServers = null),
        )
        return CredentialCreationOptions.create(enrichedOffer)
    }

    /**
     * Displays several QR Codes to start the issuance process in the Wallet,
     * including the offers for auth-code flows,
     * as well as offers for pre-authorized flows when the user is logged in.
     */
    @GetMapping("/")
    suspend fun index(
        model: ModelMap,
        session: HttpSession,
        authentication: Authentication?,
    ): ModelAndView {
        Napier.i("/index called with session ${session.id} and $authentication")
        val user = SpringSecurityAuthenticationSupplier.toOidcUserInfoExtended(authentication)
            ?: SecurityContextHolder.getContext().authentication
                ?.let { SpringSecurityAuthenticationSupplier.toOidcUserInfoExtended(it) }
        Napier.i("/index called with ${user?.userInfo?.subject}")
        val authCodeTabs = listOf(
            buildTabItemAuthCode("All", "All credentials with auth code", setOf(), Paths.Schemes.HaipVci)
        ) + credentialOfferings.map { offering ->
            buildTabItemAuthCode(
                title = offering.tabTitle(),
                description = offering.description ?: "",
                credential = offering.scheme to offering.representation,
                urlScheme = offering.urlScheme(),
            )
        }
        val preAuthTabs = user?.let { u ->
            listOf(
                buildTabItemPreAuthn(u, "All (pre-auth)", "All credentials with pre-authn", setOf(), Paths.Schemes.HaipVci)
            ) + credentialOfferings.map { offering ->
                buildTabItemPreAuthn(
                    user = u,
                    title = offering.tabTitle(preAuth = true),
                    description = offering.description ?: "",
                    credential = offering.scheme to offering.representation,
                    urlScheme = offering.urlScheme(),
                )
            }
        } ?: listOf()
        model["tabs"] = authCodeTabs + preAuthTabs
        return ModelAndView("index")
    }

    private fun CredentialOffering.urlScheme() =
        if (scheme.isoDocType == AV_DOCTYPE) Paths.Schemes.Av else Paths.Schemes.HaipVci

    private fun CredentialOffering.tabTitle(preAuth: Boolean = false) =
        "$name · ${representation.label()}" + if (preAuth) " (pre-auth)" else ""

    private fun CredentialRepresentation.label() = when (this) {
        SD_JWT -> "SD-JWT"
        ISO_MDOC -> "mdoc"
        PLAIN_JWT -> "JWT"
    }

    private suspend fun buildTabItemPreAuthn(
        user: OidcUserInfoExtended,
        title: String,
        description: String,
        credential: Pair<CredentialScheme, CredentialRepresentation>,
        urlScheme: String,
    ) = buildTabItemPreAuthn(
        user = user,
        title = title,
        description = description,
        credentials = setOf(credential),
        urlScheme = urlScheme
    )

    private suspend fun buildTabItemPreAuthn(
        user: OidcUserInfoExtended,
        title: String,
        description: String,
        credentials: Set<Pair<CredentialScheme, CredentialRepresentation>>,
        urlScheme: String,
    ): TabItem = buildTabItem(
        offer = authorizationService.offerWithPreAuthnForUserForSchemes(
            user = user,
            credentialIssuer = credentialIssuer.metadata.credentialIssuer,
            schemes = credentials
        ),
        urlScheme = urlScheme,
        title = title,
        description = description,
        preAuth = true,
    )

    private suspend fun buildTabItemAuthCode(
        title: String,
        description: String,
        credential: Pair<CredentialScheme, CredentialRepresentation>,
        urlScheme: String,
    ): TabItem = buildTabItemAuthCode(
        title = title,
        description = description,
        credentials = setOf(credential),
        urlScheme = urlScheme
    )

    private suspend fun buildTabItemAuthCode(
        title: String,
        description: String,
        credentials: Set<Pair<CredentialScheme, CredentialRepresentation>>,
        urlScheme: String,
    ): TabItem = buildTabItem(
        offer = authorizationService.offerWithAuthorizationCodeForSchemes(
            credentialIssuer = credentialIssuer.metadata.credentialIssuer,
            schemes = credentials
        ),
        urlScheme = urlScheme,
        title = title,
        description = description,
        preAuth = false,
    )

    private suspend fun buildTabItem(
        offer: CredentialOffer,
        urlScheme: String,
        title: String,
        description: String,
        preAuth: Boolean,
    ): TabItem = run {
        val nonce = uuid4().toString().also { nonceToOfferMap.put(it, offer) }
        val credentialOfferUrl = backendConfigurationProperties.publicContext.appendPath(Paths.OfferUrl + "/" + nonce)
        val url = UriComponentsBuilder.fromUriString("$urlScheme://")
            .queryParam(Paths.QueryParams.CredentialOfferUri, credentialOfferUrl)
            .toUriString()
        val qrBase64 = QRCode.ofSquares().build(url).render().getBytes().encodeToString(Base64())
        TabItem(nonce, title, description, qrBase64, url, preAuth)
    }

    data class TabItem(
        val id: String,
        val title: String,
        val description: String,
        val qrBase64: String,
        val offerUrl: String,
        val preAuth: Boolean,
    )

}
