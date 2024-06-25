package at.asitplus.wallet.backend.controller

import at.asitplus.wallet.lib.oidc.AuthenticationRequestParameters
import at.asitplus.wallet.lib.oidc.AuthenticationResponseResult
import at.asitplus.wallet.lib.oidc.OpenIdConstants
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.CredentialRequestParameters
import at.asitplus.wallet.lib.oidvci.IssuerMetadata
import at.asitplus.wallet.lib.oidvci.OAuth2Error
import at.asitplus.wallet.lib.oidvci.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.TokenRequestParameters
import at.asitplus.wallet.lib.oidvci.decodeFromPostBody
import at.asitplus.wallet.lib.oidvci.decodeFromUrlQuery
import at.asitplus.wallet.lib.oidvci.jsonSerializer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Provides endpoints in the EIDAS deployment:
 * - OID4VCI for issuing credentials after a device binding
 */
@RestController
class OpenId4VciController(
    private val credentialIssuer: CredentialIssuer,
    private val authorizationService: SimpleAuthorizationService,
) {

    @GetMapping(OpenIdConstants.PATH_WELL_KNOWN_CREDENTIAL_ISSUER, produces = [APPLICATION_JSON_VALUE])
    fun metadata(): ResponseEntity<IssuerMetadata> {
        val metadata = credentialIssuer.metadata
        Napier.i("${OpenIdConstants.PATH_WELL_KNOWN_CREDENTIAL_ISSUER} returns $metadata")
        return ResponseEntity.ok(metadata)
    }

    @RequestMapping("/offer", method = [RequestMethod.GET], produces = [APPLICATION_JSON_VALUE])
    fun offer(): ResponseEntity<*> = runBlocking {
        Napier.i("/offer called")
        val offer = credentialIssuer.credentialOffer()
        Napier.d("/offer returns $offer")
        return@runBlocking ResponseEntity.ok(offer)
    }

    @RequestMapping("/authorize", method = [RequestMethod.POST, RequestMethod.GET], produces = [APPLICATION_JSON_VALUE])
    fun authorize(
        @RequestParam requestParams: Map<String, String>,
        @RequestBody requestBody: String?
    ): ResponseEntity<*> = runBlocking {
        Napier.i("/authorize called")
        Napier.v("/authorize called with $requestParams and $requestBody")
        val params: AuthenticationRequestParameters =
            if (requestBody.isNullOrEmpty()) requestParams.decodeFromUrlQuery()
            else requestBody.decodeFromPostBody()
        val result = authorizationService.authorize(params).getOrElse {
            Napier.w("/authorize got error", it)
            return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)
        }
        if (result !is AuthenticationResponseResult.Redirect) {
            Napier.w("/authorize unsupported $result")
            return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)
        }
        Napier.d("/authorize returns ${result.url}")
        return@runBlocking buildOidcRedirect(result.url)
    }

    @RequestMapping("/token", method = [RequestMethod.POST], produces = [APPLICATION_JSON_VALUE])
    fun token(@RequestBody requestBody: String): ResponseEntity<*> = runBlocking {
        Napier.i("/token called")
        Napier.v("/token called with $requestBody")
        val params: TokenRequestParameters = requestBody.decodeFromPostBody()
            ?: return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)
        val result = authorizationService.token(params).getOrElse {
            Napier.w("/token got error", it)
            return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)
        }
        Napier.d("/token returns $result")
        return@runBlocking ResponseEntity.ok(Json.encodeToString(result))
    }

    @RequestMapping("/credential", method = [RequestMethod.POST], produces = [APPLICATION_JSON_VALUE])
    fun credential(
        @RequestBody requestBody: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorizationHeader: String
    ): ResponseEntity<*> = runBlocking {
        Napier.i("/credential called")
        Napier.v("/credential called with $authorizationHeader and $requestBody")
        val params: CredentialRequestParameters = jsonSerializer.decodeFromString(requestBody)
            ?: return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)

        val credential = credentialIssuer.credential(authorizationHeader.removePrefix("Bearer "), params).getOrElse {
            Napier.w("/credential got error", it)
            return@runBlocking buildOidcErrorResponse(OpenIdConstants.Errors.INVALID_REQUEST)
        }
        Napier.d("/credential returns $credential")
        return@runBlocking ResponseEntity.ok(Json.encodeToString(credential))
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

}