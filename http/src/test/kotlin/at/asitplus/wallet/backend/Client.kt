package at.asitplus.wallet.backend

import at.asitplus.catching
import at.asitplus.openid.CredentialResponseParameters
import at.asitplus.openid.TokenResponseParameters
import at.asitplus.signum.indispensable.josef.JsonWebToken
import at.asitplus.wallet.backend.auth.SpringSecurityAuthenticationSupplier.toOidcUserInfoExtended
import at.asitplus.wallet.backend.data.OidcIssuerCredentialDataProvider
import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.jws.JwsHeaderCertOrJwk
import at.asitplus.wallet.lib.jws.SignJwt
import at.asitplus.wallet.lib.jws.SignJwtFun
import at.asitplus.wallet.lib.oauth2.OAuth2Client
import at.asitplus.wallet.lib.oauth2.RequestInfo
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.BuildDPoPHeader
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.WalletService
import at.asitplus.wallet.lib.oidvci.WalletService.RequestOptions
import at.asitplus.wallet.lib.openid.AuthenticationResponseResult
import com.benasher44.uuid.uuid4
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpMethod
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class Client {

    val randomKeyAdapter = EphemeralKeyWithSelfSignedCert()

    val oid4vciClient = WalletService(keyMaterial = randomKeyAdapter)

    val oauth2Client = OAuth2Client()

}

/**
 * Runs the whole issuance flow for [requestOptions] against the internal authorization server, taking the user from
 * the current Spring Security context, and returns the credential response.
 */
suspend fun loadCredential(
    credentialIssuer: CredentialIssuer,
    authorizationServer: SimpleAuthorizationService,
    requestOptions: RequestOptions,
    lifetime: Duration = 1.minutes,
): CredentialResponseParameters {
    val client = Client()
    val signDpop: SignJwtFun<JsonWebToken> = SignJwt(EphemeralKeyWithoutCert(), JwsHeaderCertOrJwk())
    val state = uuid4().toString()
    val credentialFormat = client.oid4vciClient
        .selectSupportedCredentialFormat(requestOptions, credentialIssuer.metadata)
        .shouldNotBeNull()
    val scope = credentialFormat.scope
    val authnRequest = client.oauth2Client.createAuthRequest(state, authorizationDetails = null, scope = scope)
    val authorizationCode = authorizationServer.authorize(authnRequest) {
        catching {
            toOidcUserInfoExtended(SecurityContextHolder.getContext().authentication)
                ?: throw IllegalArgumentException("No authenticated user")
        }
    }.getOrThrow()
    authorizationCode.shouldBeInstanceOf<AuthenticationResponseResult.Redirect>()
    val tokenRequest = client.oauth2Client.createTokenRequestParameters(
        OAuth2Client.AuthorizationForToken.Code(authorizationCode.params.shouldNotBeNull().code.shouldNotBeNull()),
        state = state,
        authorizationDetails = null,
        scope = scope
    )
    val accessToken: TokenResponseParameters = authorizationServer.token(
        request = tokenRequest,
        httpRequest = RequestInfo(
            url = Paths.TokenUrl,
            method = HttpMethod.Post,
            dpop = BuildDPoPHeader(
                signDpop = signDpop,
                url = Paths.TokenUrl,
                httpMethod = HttpMethod.Post.value,
                nonce = authorizationServer.getDpopNonce(),
            )
        )
    ).getOrThrow()
    val credentialRequest = client.oid4vciClient.createCredential(
        tokenResponse = accessToken,
        metadata = credentialIssuer.metadata,
        credentialFormat = credentialFormat,
        clientNonce = credentialIssuer.nonceWithDpopNonce().getOrThrow().response.clientNonce
    ).getOrThrow()
    return credentialIssuer.credential(
        authorizationHeader = accessToken.toHttpHeaderValue(),
        params = credentialRequest.first(),
        credentialDataProvider = OidcIssuerCredentialDataProvider(lifetime = lifetime),
    ).getOrThrow()
        .shouldBeInstanceOf<CredentialIssuer.CredentialResponse.Plain>()
        .response
}
