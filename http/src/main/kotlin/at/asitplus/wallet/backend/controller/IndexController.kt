package at.asitplus.wallet.backend.controller

import at.asitplus.dcapi.issuance.CredentialCreationOptions
import at.asitplus.openid.CredentialOffer
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.wallet.ageverification.AgeVerificationScheme
import at.asitplus.wallet.backend.Extensions.appendPath
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.auth.SpringSecurityAuthenticationSupplier
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtScheme
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation
import at.asitplus.wallet.lib.data.CredentialScheme
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.utils.DefaultMapStore
import at.asitplus.wallet.lib.utils.MapStore
import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
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
            buildTabItemAuthCode(
                title = "All-Code",
                description = "All credentials with auth code",
                credentials = setOf(),
                urlScheme = Paths.Schemes.HaipVci
            ),
            buildTabItemAuthCode(
                title = "PID-SD-JWT-Code",
                description = "PID in SD-JWT with auth code",
                credential = EuPidSdJwtScheme to CredentialRepresentation.SD_JWT,
                urlScheme = Paths.Schemes.HaipVci
            ),
            buildTabItemAuthCode(
                title = "PID-MDOC-Code",
                description = "PID in ISO MDOC with auth code",
                credential = EuPidScheme to CredentialRepresentation.ISO_MDOC,
                urlScheme = Paths.Schemes.HaipVci
            ),
            buildTabItemAuthCode(
                title = "MDL-MDOC-Code",
                description = "mDL in ISO MDOC with auth code",
                credential = MobileDrivingLicenceScheme to CredentialRepresentation.ISO_MDOC,
                urlScheme = Paths.Schemes.HaipVci
            ),
            buildTabItemAuthCode(
                title = "AV-MDOC-Code",
                description = "Age Verification in ISO MDOC with auth code",
                credential = AgeVerificationScheme to CredentialRepresentation.ISO_MDOC,
                urlScheme = Paths.Schemes.Av
            ),
        )
        val preAuthTabs = user?.let {
            listOf(
                buildTabItemPreAuthn(
                    user = user,
                    title = "All-pre",
                    description = "All credentials with pre-authn",
                    credentials = setOf(),
                    urlScheme = Paths.Schemes.HaipVci
                ),
                buildTabItemPreAuthn(
                    user = user,
                    title = "PID-SD-JWT-pre",
                    description = "PID in SD-JWT with pre-authn",
                    credential = EuPidSdJwtScheme to CredentialRepresentation.SD_JWT,
                    urlScheme = Paths.Schemes.HaipVci
                ),
                buildTabItemPreAuthn(
                    user = user,
                    title = "PID-MDOC-pre",
                    description = "PID in ISO MDOC with pre-authn",
                    credential = EuPidScheme to CredentialRepresentation.ISO_MDOC,
                    urlScheme = Paths.Schemes.HaipVci
                ),
                buildTabItemPreAuthn(
                    user = user,
                    title = "MDL-MDOC-pre",
                    description = "mDL in ISO MDOC with pre-authn",
                    credential = MobileDrivingLicenceScheme to CredentialRepresentation.ISO_MDOC,
                    urlScheme = Paths.Schemes.HaipVci
                ),
                buildTabItemPreAuthn(
                    user = user,
                    title = "AV-MDOC-pre",
                    description = "Age Verification in ISO MDOC with pre-authn",
                    credential = AgeVerificationScheme to CredentialRepresentation.ISO_MDOC,
                    urlScheme = Paths.Schemes.Av
                )
            )
        } ?: listOf()
        model["tabs"] = authCodeTabs + preAuthTabs
        return ModelAndView("index")
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
        description = description
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
        description = description
    )

    private suspend fun buildTabItem(
        offer: CredentialOffer,
        urlScheme: String,
        title: String,
        description: String,
    ): TabItem = run {
        val nonce = uuid4().toString().also { nonceToOfferMap.put(it, offer) }
        val credentialOfferUrl = backendConfigurationProperties.publicContext.appendPath(Paths.OfferUrl + "/" + nonce)
        val url = UriComponentsBuilder.fromUriString("$urlScheme://")
            .queryParam(Paths.QueryParams.CredentialOfferUri, credentialOfferUrl)
            .toUriString()
        val qrBase64 = QRCode.ofSquares().build(url).render().getBytes().encodeToString(Base64())
        TabItem(nonce, title, description, qrBase64, url)
    }

    data class TabItem(
        val id: String,
        val title: String,
        val description: String,
        val qrBase64: String,
        val offerUrl: String,
    )

}
