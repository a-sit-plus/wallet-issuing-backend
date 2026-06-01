package at.asitplus.wallet.backend.controller

import at.asitplus.openid.ClientNonceResponse
import at.asitplus.openid.DisplayLogoProperties
import at.asitplus.openid.DisplayProperties
import at.asitplus.openid.OpenIdConstants
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.config.MetadataConfiguration
import at.asitplus.wallet.backend.data.OidcIssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.MediaTypes
import at.asitplus.wallet.lib.ktor.openid.DPoP
import at.asitplus.wallet.lib.ktor.openid.DPoPNonce
import at.asitplus.wallet.lib.ktor.openid.OAuthClientAttestation
import at.asitplus.wallet.lib.ktor.openid.OAuthClientAttestationPop
import at.asitplus.wallet.lib.oauth2.RequestInfo
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.WalletService
import io.github.aakira.napier.Napier
import io.ktor.client.utils.CacheControl
import io.ktor.http.*
import jakarta.servlet.http.HttpServletRequest
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
    fun issuerMetadata() = credentialIssuer.metadata.copy(
        displayProperties = setOf(
            backendConfigurationProperties.metadata.toDisplayProperties()
        )
    ).also {
        Napier.i("${OpenIdConstants.PATH_WELL_KNOWN_CREDENTIAL_ISSUER} returns $it")
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
    fun jwtVcMetadata() = credentialIssuer.jwtVcMetadata.also {
        Napier.i("${OpenIdConstants.PATH_WELL_KNOWN_JWT_VC_ISSUER_METADATA} returns $it")
    }

    /**
     * Called by the Wallet to get a nonce for their proof-of-possessions, see [CredentialIssuer.nonceWithDpopNonce].
     */
    @PostMapping(Paths.NonceUrl, produces = [APPLICATION_JSON_VALUE])
    suspend fun nonce(): ResponseEntity<ClientNonceResponse> {
        Napier.i("${Paths.NonceUrl} called")
        val result = credentialIssuer.nonceWithDpopNonce().getOrElse {
            Napier.w("${Paths.NonceUrl} got error", it)
            throw it
        }
        Napier.d("${Paths.NonceUrl} returns $result")
        return ResponseEntity.status(HttpStatus.OK)
            .header(HttpHeaders.CacheControl, CacheControl.NO_STORE)
            .apply { result.dpopNonce?.let { header(HttpHeaders.DPoPNonce, it) } }
            .body(result.response)
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
    suspend fun credential(
        @RequestBody requestBody: String,
        request: HttpServletRequest,
    ): ResponseEntity<*> {
        Napier.i("${Paths.CredentialUrl} called")
        val authorizationHeader = request.getHeader(HttpHeaders.Authorization)
        Napier.v("${Paths.CredentialUrl} called with $authorizationHeader and $requestBody")
        val params = WalletService.CredentialRequest.parse(requestBody).getOrElse {
            Napier.w("${Paths.CredentialUrl} can't parse request", it)
            throw it
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
            throw it
        }
        Napier.d("${Paths.CredentialUrl} returns $credential")
        return credential.toResponseEntity()
    }

    private suspend fun CredentialIssuer.CredentialResponse.toResponseEntity(): ResponseEntity<*> =
        when (this) {
            is CredentialIssuer.CredentialResponse.Encrypted -> this.toResponseEntity()
            is CredentialIssuer.CredentialResponse.Plain -> this.toResponseEntity()
        }

    private suspend fun CredentialIssuer.CredentialResponse.Plain.toResponseEntity() =
        ResponseEntity.status(HttpStatus.OK)
            .contentType(MediaType.APPLICATION_JSON)
            .body(response)

    private suspend fun CredentialIssuer.CredentialResponse.Encrypted.toResponseEntity() =
        ResponseEntity.status(HttpStatus.OK)
            .contentType(MediaType.parseMediaType(MediaTypes.Application.JWT))
            .body(response.serialize())

}

