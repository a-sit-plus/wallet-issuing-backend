package at.asitplus.wallet.backend.controller

import at.asitplus.openid.OpenIdConstants
import at.asitplus.wallet.backend.Extensions.toRequestInfo
import at.asitplus.wallet.backend.Extensions.toResponseEntity
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.data.OidcIssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.MediaTypes
import at.asitplus.wallet.lib.oidvci.OpenId4VciClient
import at.asitplus.wallet.lib.oidvci.OpenId4VciServer
import at.asitplus.wallet.lib.oidvci.toHttpResponse
import io.github.aakira.napier.Napier
import io.ktor.http.*
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController


/**
 * Implements controller for OpenID4VCI, mapping the public functions of [OpenId4VciServer].
 */
@RestController
class OpenId4VciController(
    private val credentialIssuer: OpenId4VciServer,
    private val backendConfigurationProperties: BackendConfigurationProperties,
) {

    @GetMapping(
        OpenIdConstants.PATH_WELL_KNOWN_CREDENTIAL_ISSUER,
        produces = [APPLICATION_JSON_VALUE, MediaTypes.Application.JWT]
    )
    suspend fun issuerMetadata(@RequestHeader(HttpHeaders.Accept, required = false) accept: String?): ResponseEntity<String> {
        val result = credentialIssuer.metadataHttpResponse(accept).getOrThrow()
        Napier.i("${OpenIdConstants.PATH_WELL_KNOWN_CREDENTIAL_ISSUER} returns $result")
        return result.toResponseEntity()
    }

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
     * Called by the Wallet to get a nonce for their proof-of-possessions, see [OpenId4VciServer.nonceWithDpopNonce].
     */
    @PostMapping(Paths.NonceUrl, produces = [APPLICATION_JSON_VALUE])
    suspend fun nonce(): ResponseEntity<String> {
        Napier.i("${Paths.NonceUrl} called")
        val result = credentialIssuer.nonceWithDpopNonce().getOrElse {
            Napier.w("${Paths.NonceUrl} got error", it)
            throw it
        }
        Napier.d("${Paths.NonceUrl} returns $result")
        return result.toHttpResponse().toResponseEntity()
    }

    /**
     * Issues the credential, when the token sent by the Wallet is valid,
     * see [OpenId4VciServer.credential].
     */
    @PostMapping(Paths.CredentialUrl, produces = [APPLICATION_JSON_VALUE, MediaTypes.Application.JWT])
    suspend fun credential(
        @RequestBody requestBody: String,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        Napier.i("${Paths.CredentialUrl} called")
        val authorizationHeader = request.getHeader(HttpHeaders.Authorization)
        Napier.v("${Paths.CredentialUrl} called with $authorizationHeader and $requestBody")
        val params = OpenId4VciClient.CredentialRequest.parse(requestBody).getOrElse {
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
        return credential.toHttpResponse().toResponseEntity()
    }

}
