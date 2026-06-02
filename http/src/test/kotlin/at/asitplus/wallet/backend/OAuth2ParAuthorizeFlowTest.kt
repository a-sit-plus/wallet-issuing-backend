package at.asitplus.wallet.backend

import at.asitplus.openid.PushedAuthenticationResponseParameters
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.lib.oauth2.OAuth2Client
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.encodeToParameters
import at.asitplus.wallet.lib.oidvci.formUrlEncode
import com.benasher44.uuid.uuid4
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.test.runTest
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
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
}
