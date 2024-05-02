package at.asitplus.wallet.backend.controller

import at.asitplus.wallet.lib.oidc.OpenIdConstants
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.CredentialRequestParameters
import at.asitplus.wallet.lib.oidvci.IssuerMetadata
import at.asitplus.wallet.lib.oidvci.OAuth2Error
import at.asitplus.wallet.lib.oidvci.OAuth2Exception
import at.asitplus.wallet.lib.oidvci.jsonSerializer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

/**
 * Provides endpoints in the EIDAS deployment:
 * - OID4VCI for issuing credentials after a device binding
 */
@RestController
class OpenId4VciController(
    private val credentialIssuer: CredentialIssuer,
) {

    @GetMapping(OpenIdConstants.PATH_WELL_KNOWN_CREDENTIAL_ISSUER)
    fun metadata(): ResponseEntity<IssuerMetadata> {
        val metadata = credentialIssuer.metadata
        Napier.i("${OpenIdConstants.PATH_WELL_KNOWN_CREDENTIAL_ISSUER} returns $metadata")
        return ResponseEntity.ok(metadata)
    }

    @RequestMapping("/offer", method = [RequestMethod.GET])
    fun offer(): ResponseEntity<*> = runBlocking {
        Napier.i("/offer called")
        return@runBlocking suspendingOffer()
    }

    private suspend fun suspendingOffer(): ResponseEntity<out Any> {
        val offer = credentialIssuer.credentialOffer()
        Napier.d("/offer returns $offer")
        return ResponseEntity.ok(jsonSerializer.encodeToString(offer))
    }

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
            val credential = credentialIssuer.credential(authorizationHeader, params)
            Napier.d("/credential returns $credential")
            ResponseEntity.ok(jsonSerializer.encodeToString(credential))
        } catch (e: OAuth2Exception) {
            Napier.w("/credential error", e)
            buildOidcErrorResponse(e.error)
        }
    }

    private fun buildOidcErrorResponse(error: String): ResponseEntity<OAuth2Error> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(OAuth2Error(error = error))
    }

}