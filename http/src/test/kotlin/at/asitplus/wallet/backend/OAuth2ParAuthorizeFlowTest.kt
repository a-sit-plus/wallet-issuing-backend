package at.asitplus.wallet.backend

import at.asitplus.catching
import at.asitplus.openid.PushedAuthenticationResponseParameters
import at.asitplus.signum.indispensable.josef.JsonWebToken
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.backend.auth.SpringSecurityAuthenticationSupplier.toOidcUserInfoExtended
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.jws.JwsHeaderCertOrJwk
import at.asitplus.wallet.lib.jws.SignJwt
import at.asitplus.wallet.lib.ktor.openid.DPoP
import at.asitplus.wallet.lib.ktor.openid.DPoPNonce
import at.asitplus.wallet.lib.oauth2.OAuth2Client
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.BuildDPoPHeader
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.encodeToParameters
import at.asitplus.wallet.lib.oidvci.formUrlEncode
import at.asitplus.wallet.lib.openid.AuthenticationResponseResult
import com.benasher44.uuid.uuid4
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpHeaders as KtorHttpHeaders
import kotlinx.coroutines.test.runTest
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.emptyString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class OAuth2ParAuthorizeFlowTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var credentialIssuer: CredentialIssuer

    @Autowired
    private lateinit var authorizationServer: SimpleAuthorizationService

    @Test
    fun `authorize still requires authentication on initial request`() {
        mockMvc.get(Paths.AuthorizeUrl).andExpect {
            status { isFound() }
            header { string(HttpHeaders.LOCATION, containsString(Paths.LoginUrl)) }
        }
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun `par followed by authorize with request_uri succeeds when issuer_state is present`() = runTest {
        val oauth2Client = OAuth2Client()
        val scope = credentialIssuer.metadata.supportedCredentialConfigurations
            ?.values?.firstNotNullOfOrNull { it.scope }
            ?: error("No scope configured in issuer metadata")
        val issuerState = authorizationServer.offerWithAuthorizationCodeForSchemes(
            credentialIssuer = credentialIssuer.metadata.credentialIssuer,
            schemes = emptySet()
        ).grants?.authorizationCode?.issuerState
            ?: error("No issuer_state in generated credential offer")
        val state = uuid4().toString()
        val authRequest = oauth2Client.createAuthRequest(
            state = state,
            scope = scope,
            issuerState = issuerState,
        )
        val parBody = (authRequest.encodeToParameters()).formUrlEncode()

        val parResult = mockMvc.perform(
            asyncDispatch(
                mockMvc.post(Paths.ParUrl) {
                    contentType = MediaType.APPLICATION_FORM_URLENCODED
                    content = parBody
                }.andExpect { request { asyncStarted() } }.andReturn()
            )
        ).andExpect(status().isCreated).andReturn()

        val parResponse: PushedAuthenticationResponseParameters =
            joseCompliantSerializer.decodeFromString(parResult.response.contentAsString)

        mockMvc.perform(
            asyncDispatch(
                mockMvc.get(Paths.AuthorizeUrl) {
                    param("client_id", oauth2Client.clientId)
                    param("request_uri", parResponse.requestUri.shouldNotBeNull())
                }.andExpect { request { asyncStarted() } }.andReturn()
            )
        ).andExpect(status().isFound)
            .andExpect(header().string(HttpHeaders.LOCATION, containsString("code=")))
            .andExpect(header().string(HttpHeaders.LOCATION, containsString("state=$state")))
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun `token error response includes DPoP nonce when proof nonce is missing`() = runTest {
        val oauth2Client = OAuth2Client()
        val scope = credentialIssuer.metadata.supportedCredentialConfigurations
            ?.values?.firstNotNullOfOrNull { it.scope }
            ?: error("No scope configured in issuer metadata")
        val state = uuid4().toString()
        val authRequest = oauth2Client.createAuthRequest(state = state, scope = scope)
        val authorizationCode = authorizationServer.authorize(authRequest) {
            catching {
                toOidcUserInfoExtended(SecurityContextHolder.getContext().authentication)
                    ?: error("No authenticated user")
            }
        }.getOrThrow()
        authorizationCode.shouldBeInstanceOf<AuthenticationResponseResult.Redirect>()
        val tokenRequest = oauth2Client.createTokenRequestParameters(
            authorization = OAuth2Client.AuthorizationForToken.Code(
                authorizationCode.params.shouldNotBeNull().code.shouldNotBeNull()
            ),
            state = state,
            scope = scope,
        )
        val dpop = BuildDPoPHeader(
            signDpop = SignJwt<JsonWebToken>(EphemeralKeyWithoutCert(), JwsHeaderCertOrJwk()),
            url = Paths.TokenUrl,
        )

        val tokenResult = mockMvc.post(Paths.TokenUrl) {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            content = tokenRequest.encodeToParameters().formUrlEncode()
            header(KtorHttpHeaders.DPoP, dpop)
        }.andExpect { request { asyncStarted() } }.andReturn()

        mockMvc.perform(asyncDispatch(tokenResult))
            .andExpect(status().isBadRequest)
            .andExpect(header().string(KtorHttpHeaders.DPoPNonce, not(emptyString())))
    }
}
