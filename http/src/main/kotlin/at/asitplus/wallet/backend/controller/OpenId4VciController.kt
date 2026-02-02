package at.asitplus.wallet.backend.controller

import at.asitplus.openid.DisplayLogoProperties
import at.asitplus.openid.DisplayProperties
import at.asitplus.openid.IssuerMetadata
import at.asitplus.openid.JwtVcIssuerMetadata
import at.asitplus.openid.OpenIdConstants
import at.asitplus.openid.OpenIdConstants.Errors
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.config.MetadataConfiguration
import at.asitplus.wallet.backend.data.OidcIssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.MediaTypes
import at.asitplus.wallet.lib.data.vckJsonSerializer
import at.asitplus.wallet.lib.ktor.openid.DPoP
import at.asitplus.wallet.lib.ktor.openid.DPoPNonce
import at.asitplus.wallet.lib.ktor.openid.OAuthClientAttestation
import at.asitplus.wallet.lib.ktor.openid.OAuthClientAttestationPop
import at.asitplus.wallet.lib.oauth2.RequestInfo
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.OAuth2Error
import at.asitplus.wallet.lib.oidvci.OAuth2Exception
import at.asitplus.wallet.lib.oidvci.WalletService
import io.github.aakira.napier.Napier
import io.ktor.client.utils.CacheControl
import io.ktor.http.*
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController


/**
 * Implements controller for OpenID4VCI, mapping the public functions of [CredentialIssuer].
 */
@RestController
class OpenId4VciController(
    private val credentialIssuer: CredentialIssuer,
    private val backendConfigurationProperties: BackendConfigurationProperties,
) {

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
        logo = DisplayLogoProperties(uri = logo)
    )

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

    /**
     * Called by the Wallet to get a nonce for their proof-of-possessions, see [CredentialIssuer.nonceWithDpopNonce].
     */
    @PostMapping(Paths.NonceUrl, produces = [APPLICATION_JSON_VALUE])
    fun nonce(
    ): ResponseEntity<*> = runBlocking {
        Napier.i("${Paths.NonceUrl} called")
        val result = credentialIssuer.nonceWithDpopNonce().getOrElse {
            Napier.w("${Paths.NonceUrl} got error", it)
            return@runBlocking buildOidcErrorResponse(it)
        }
        Napier.d("${Paths.NonceUrl} returns $result")
        ResponseEntity.status(HttpStatus.OK)
            .header(HttpHeaders.CacheControl, CacheControl.NO_STORE)
            .header(HttpHeaders.DPoPNonce, result.dpopNonce)
            .body(vckJsonSerializer.encodeToString(result.response))
    }

    private fun HttpServletRequest.toRequestInfo() = RequestInfo(
        url = requestURL.toString(),
        method = HttpMethod.parse(method),
        dpop = getHeader(HttpHeaders.DPoP),
        clientAttestation = getHeader(HttpHeaders.OAuthClientAttestation),
        clientAttestationPop = getHeader(HttpHeaders.OAuthClientAttestationPop),
    )

    /**
     * Issues the credential, when the token sent by the Wallet is valid,
     * see [CredentialIssuer.credential].
     */
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
            Napier.w("${Paths.CredentialUrl} got error", it)
            return@runBlocking buildOidcErrorResponse(it)
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
            .contentType(MediaType.APPLICATION_JSON)
            .body(vckJsonSerializer.encodeToString(response))

    private suspend fun CredentialIssuer.CredentialResponse.Encrypted.toResponseEntity(): ResponseEntity<String?> =
        ResponseEntity
            .status(HttpStatus.OK)
            .contentType(MediaType.parseMediaType(MediaTypes.Application.JWT))
            .body(vckJsonSerializer.encodeToString(response.serialize()))

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


