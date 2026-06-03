package at.asitplus.wallet.backend

import at.asitplus.catching
import at.asitplus.openid.CredentialResponseParameters
import at.asitplus.openid.TokenResponseParameters
import at.asitplus.signum.indispensable.josef.JsonWebToken
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.backend.auth.SpringSecurityAuthenticationSupplier.toOidcUserInfoExtended
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtScheme
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.SD_JWT
import at.asitplus.wallet.lib.jws.JwsHeaderCertOrJwk
import at.asitplus.wallet.lib.jws.SignJwt
import at.asitplus.wallet.lib.jws.SignJwtFun
import at.asitplus.wallet.lib.ktor.openid.DPoP
import at.asitplus.wallet.lib.oauth2.OAuth2Client
import at.asitplus.wallet.lib.oauth2.RequestInfo
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.BuildDPoPHeader
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.WalletService
import at.asitplus.wallet.lib.openid.AuthenticationResponseResult
import com.benasher44.uuid.uuid4
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpHeaders as KtorHttpHeaders
import io.ktor.http.HttpMethod as KtorHttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CredentialEndpointSerializationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var credentialIssuer: CredentialIssuer

    @Autowired
    private lateinit var authorizationServer: SimpleAuthorizationService

    @Test
    @WithOAuth2AuthenticationToken
    fun `credential endpoint serializes JsonElement credential as JSON value`() = runTest {
        val client = Client()
        val signDpop: SignJwtFun<JsonWebToken> = SignJwt(EphemeralKeyWithoutCert(), JwsHeaderCertOrJwk())
        val (accessToken, credentialRequest) = authorizeAndCreateCredentialRequest(client, signDpop)
        val credentialDpop = BuildDPoPHeader(
            signDpop = signDpop,
            url = "http://localhost${Paths.CredentialUrl}",
            httpMethod = KtorHttpMethod.Post.value,
            accessToken = accessToken.accessToken,
            nonce = authorizationServer.getDpopNonce(),
        )

        val credentialResult = mockMvc.post(Paths.CredentialUrl) {
            contentType = MediaType.APPLICATION_JSON
            header(HttpHeaders.AUTHORIZATION, accessToken.toHttpHeaderValue())
            header(KtorHttpHeaders.DPoP, credentialDpop)
            content = joseCompliantSerializer.encodeToString(credentialRequest.request)
        }.andExpect { request { asyncStarted() } }.andReturn()

        val responseBody = mockMvc.perform(asyncDispatch(credentialResult))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        responseBody shouldNotContain "coerceToInlineType"
        joseCompliantSerializer.decodeFromString<CredentialResponseParameters>(responseBody)
            .credentials.shouldNotBeNull()
            .first().credentialString.shouldNotBeNull()
    }

    private suspend fun authorizeAndCreateCredentialRequest(
        client: Client,
        signDpop: SignJwtFun<JsonWebToken>,
    ): Pair<TokenResponseParameters, WalletService.CredentialRequest.Plain> {
        val credentialFormat = client.oid4vciClient
            .selectSupportedCredentialFormat(
                WalletService.RequestOptions(EuPidSdJwtScheme, SD_JWT),
                credentialIssuer.metadata,
            ).shouldNotBeNull()
        val scope = credentialFormat.scope
        val state = uuid4().toString()
        val authnRequest = client.oauth2Client.createAuthRequest(
            state = state,
            authorizationDetails = null,
            scope = scope,
        )
        val authorizationCode = authorizationServer.authorize(authnRequest) {
            catching {
                toOidcUserInfoExtended(SecurityContextHolder.getContext().authentication)
                    ?: error("No authenticated user")
            }
        }.getOrThrow()
        authorizationCode.shouldBeInstanceOf<AuthenticationResponseResult.Redirect>()
        val tokenRequest = client.oauth2Client.createTokenRequestParameters(
            authorization = OAuth2Client.AuthorizationForToken.Code(
                authorizationCode.params.shouldNotBeNull().code.shouldNotBeNull()
            ),
            state = state,
            authorizationDetails = null,
            scope = scope,
        )
        val accessToken = authorizationServer.token(
            request = tokenRequest,
            httpRequest = RequestInfo(
                url = Paths.TokenUrl,
                method = KtorHttpMethod.Post,
                dpop = BuildDPoPHeader(
                    signDpop = signDpop,
                    url = Paths.TokenUrl,
                    httpMethod = KtorHttpMethod.Post.value,
                    nonce = authorizationServer.getDpopNonce(),
                ),
            ),
        ).getOrThrow()
        val credentialNonce = credentialIssuer.nonceWithDpopNonce().getOrThrow()
        val credentialRequest = client.oid4vciClient.createCredential(
            tokenResponse = accessToken,
            metadata = credentialIssuer.metadata,
            credentialFormat = credentialFormat,
            clientNonce = credentialNonce.response.clientNonce,
        ).getOrThrow().first()
            .shouldBeInstanceOf<WalletService.CredentialRequest.Plain>()

        return accessToken to credentialRequest
    }
}
