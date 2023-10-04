package at.asitplus.wallet.backend.controller

import at.asitplus.wallet.backend.ProfileConstants
import at.asitplus.wallet.backend.auth.WebSecurityConstants.AUTHORITY_DEVICE_BINDING
import at.asitplus.wallet.lib.oidc.AuthenticationRequestParameters
import at.asitplus.wallet.lib.oidc.OpenIdConstants
import at.asitplus.wallet.lib.oidvci.CredentialRequestParameters
import at.asitplus.wallet.lib.oidvci.IssuerMetadata
import at.asitplus.wallet.lib.oidvci.IssuerService
import at.asitplus.wallet.lib.oidvci.OAuth2Error
import at.asitplus.wallet.lib.oidvci.OAuth2Exception
import at.asitplus.wallet.lib.oidvci.TokenRequestParameters
import at.asitplus.wallet.lib.oidvci.decodeFromPostBody
import at.asitplus.wallet.lib.oidvci.decodeFromUrlQuery
import at.asitplus.wallet.lib.oidvci.jsonSerializer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpServerErrorException.InternalServerError
import java.util.*

/**
 * Provides endpoints in the EIDAS deployment:
 * - OID4VCI for issuing credentials after a device binding
 */
@Profile(ProfileConstants.EIDASID)
@RestController
class OpenId4VciController(
    private val issuerService: IssuerService,
) {

    @GetMapping(OpenIdConstants.PATH_WELL_KNOWN_CREDENTIAL_ISSUER)
    fun metadata(): ResponseEntity<IssuerMetadata> {
        val metadata = issuerService.metadata
        Napier.i("${OpenIdConstants.PATH_WELL_KNOWN_CREDENTIAL_ISSUER} returns $metadata")
        return ResponseEntity.ok(metadata)
    }

    @PreAuthorize("hasAuthority(\"$AUTHORITY_DEVICE_BINDING\")")
    @RequestMapping("/authorize", method = [RequestMethod.POST, RequestMethod.GET])
    fun authorize(
        @RequestParam requestParams: Map<String, String>,
        @RequestBody requestBody: String?
    ): ResponseEntity<String> {
        Napier.i("/authorize called")
        Napier.v("/authorize called with $requestParams and $requestBody")
        val params: AuthenticationRequestParameters =
            if (requestBody.isNullOrEmpty()) requestParams.decodeFromUrlQuery()
            else requestBody.decodeFromPostBody()
        val location = issuerService.authorize(params)?: TODO("FIXME")
        Napier.d("/authorize returns $location")
        return buildOidcRedirect(location)
    }

    @PreAuthorize("hasAuthority(\"$AUTHORITY_DEVICE_BINDING\")")
    @RequestMapping("/token", method = [RequestMethod.POST])
    fun token(@RequestBody requestBody: String): ResponseEntity<*> {
        Napier.i("/token called")
        Napier.v("/token called with $requestBody")
        val params: TokenRequestParameters = requestBody.decodeFromPostBody()
            ?: return buildOidcErrorResponse("invalid_request")
        return try {
            val result = issuerService.token(params)
            Napier.d("/token returns $result")
            ResponseEntity.ok(Json.encodeToString(result))
        } catch (e: OAuth2Exception) {
            Napier.w("/token error $e, $e.error")
            buildOidcErrorResponse(e.error)
        }
    }

    @PreAuthorize("hasAuthority(\"$AUTHORITY_DEVICE_BINDING\")")
    @RequestMapping("/credential", method = [RequestMethod.POST])
    fun credential(
        @RequestBody requestBody: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorizationHeader: String
    ): ResponseEntity<*> = runBlocking {
        Napier.i("/credential called")
        Napier.v("/credential called with $authorizationHeader and $requestBody")
        return@runBlocking suspendingCredential(authorizationHeader, requestBody)
    }

    private suspend fun suspendingCredential(
        authorizationHeader: String,
        requestBody: String
    ): ResponseEntity<out Any> {
        val params: CredentialRequestParameters = jsonSerializer.decodeFromString(requestBody)
            ?: return buildOidcErrorResponse("invalid_request")
        return try {
            val credential = issuerService.credential(authorizationHeader, params)
            Napier.d("/credential returns $credential")
            ResponseEntity.ok(jsonSerializer.encodeToString(credential))
        } catch (e: OAuth2Exception) {
            Napier.w("/credential error $e, $e.error")
            buildOidcErrorResponse(e.error)
        }
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