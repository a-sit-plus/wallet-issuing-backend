package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.auth.AuthenticatedDeviceBindingUser
import at.asitplus.wallet.backend.auth.WebSecurityConstants
import at.asitplus.wallet.backend.auth.WebSecurityConstants.AUTHORITY_OIDC
import at.asitplus.wallet.lib.agent.DefaultCryptoService
import at.asitplus.wallet.lib.oidc.OpenIdConstants
import at.asitplus.wallet.lib.oidvci.IssuerMetadata
import at.asitplus.wallet.lib.oidvci.TokenResponseParameters
import at.asitplus.wallet.lib.oidvci.WalletService
import at.asitplus.wallet.lib.oidvci.encodeToParameters
import at.asitplus.wallet.lib.oidvci.formUrlEncode
import at.asitplus.wallet.pupilid.BindingConfirmRequestJ
import at.asitplus.wallet.pupilid.BindingCsrRequestJ
import at.asitplus.wallet.pupilid.BindingParamsRequestJ
import at.asitplus.wallet.pupilid.BindingParamsResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.random.Random


/**
 * Test situation where user authenticates using OpenId from their mobile device, i.e. with the ID Austria App,
 * establishes a session to this service, and then creates a device binding, and then requests credentials
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.SYSTEM_OUT)
class OpenId4VciControllerTestISO {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    private lateinit var clientRedirectUrl: String
    private lateinit var walletService: WalletService

    private lateinit var bpk: String
    private lateinit var deviceName: String
    private lateinit var bindingClient: Client

    @BeforeEach
    fun beforeEach() {
        bpk = UUID.randomUUID().toString()
        deviceName = UUID.randomUUID().toString()
        bindingClient = Client()
        clientRedirectUrl = "https://localhost/${UUID.randomUUID()}"
        walletService = WalletService(
            credentialScheme = at.asitplus.wallet.lib.data.ConstantIndex.MobileDrivingLicence2023,
            clientId = "https://wallet.a-sit.at/mobile",
            redirectUrl = clientRedirectUrl,
            cryptoService = DefaultCryptoService(bindingClient.keyPair)
        )
    }

    @Test
    fun metadata_unauthenticated() {
        mockMvc.get(OpenIdConstants.PATH_WELL_KNOWN_CREDENTIAL_ISSUER)
            .andExpect {
                status { isOk() }
            }.andReturn()
    }

    @Test
    fun authorize_unauthenticated() {
        mockMvc.post("/authorize") {
            walletService.createAuthRequest().encodeToParameters().forEach {
                this.param(it.key, it.value)
            }
        }.andExpect {
            status { isForbidden() }
        }.andReturn()
    }

    @Test
    fun token_unauthenticated() {
        mockMvc.post("/token") {
            content = walletService.createTokenRequestParameters(UUID.randomUUID().toString()).encodeToParameters()
        }.andExpect {
            status { isForbidden() }
        }.andReturn()
    }

    @Test
    fun credential_unauthenticated() {
        mockMvc.post("/credential") {
            content = walletService.createAuthRequest().encodeToParameters()
            header(HttpHeaders.AUTHORIZATION, "Bearer foo")
        }.andExpect {
            status { isForbidden() }
        }.andReturn()
    }

    @Test
    fun authorize_with_oidc_login() {
        mockMvc.post("/authorize") {
            withOidcLoginIdToken()
            walletService.createAuthRequest().encodeToParameters().forEach {
                this.param(it.key, it.value)
            }
        }.andExpect {
            status { isForbidden() }
        }.andReturn()
    }

    @Test
    fun authorize_success_with_device_binding() {
        val bindingUser = AuthenticatedDeviceBindingUser(UUID.randomUUID().toString(), Random.nextBytes(32))
        val result = mockMvc.post("/authorize") {
            with(user(bindingUser))
            walletService.createAuthRequest().encodeToParameters().forEach {
                this.param(it.key, it.value)
            }
        }.andExpect {
            // Redirect means success, as the client gets an authorization code back
            status { is3xxRedirection() }
        }.andReturn()

        val redirectUrl = result.response.getHeader(HttpHeaders.LOCATION)
        redirectUrl.shouldNotBeNull()
        redirectUrl shouldStartWith clientRedirectUrl
        val code = Url(redirectUrl).parameters["code"]
        code.shouldNotBeNull()
    }

    @Test
    fun binding_with_oidc_then_issuance() {
        val startRequest = BindingParamsRequestJ(UUID.randomUUID().toString())

        val startResponse = mockMvc.post("/binding/start") {
            withOidcLoginIdToken()
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(startRequest)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val bindingParamsResponse = mapper.readValue<BindingParamsResponse>(startResponse.response.contentAsString)
        val challenge = bindingParamsResponse.challenge
        val subject = bindingParamsResponse.subject

        val xAuthToken = startResponse.response.getHeaderValue(WebSecurityConstants.X_AUTH_TOKEN)!!
        val csrRequest = BindingCsrRequestJ(challenge, bindingClient.generateCsr(subject), deviceName, listOf())

        mockMvc.post("/binding/create") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(csrRequest)
            header(WebSecurityConstants.X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val confirmRequest = BindingConfirmRequestJ(true)

        mockMvc.post("/binding/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(confirmRequest)
            header(WebSecurityConstants.X_AUTH_TOKEN, xAuthToken)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val metadata: IssuerMetadata = Json.decodeFromString(
            mockMvc.get(OpenIdConstants.PATH_WELL_KNOWN_CREDENTIAL_ISSUER) {
                accept = MediaType.APPLICATION_JSON
            }.andReturn().response.contentAsString
        )

        val authorizeResult = mockMvc.post("/authorize") {
            header(WebSecurityConstants.X_AUTH_TOKEN, xAuthToken)
            walletService.createAuthRequest().encodeToParameters().forEach {
                this.param(it.key, it.value)
            }
        }.andExpect { status { is3xxRedirection() } }
            // Redirect means success, as the client gets an authorization code back
            .andReturn()

        val redirectUrl = authorizeResult.response.getHeader(HttpHeaders.LOCATION)
        redirectUrl.shouldNotBeNull()
        redirectUrl shouldStartWith clientRedirectUrl
        val code = Url(redirectUrl).parameters["code"]
        code.shouldNotBeNull()

        val tokenResult = mockMvc.post("/token") {
            header(WebSecurityConstants.X_AUTH_TOKEN, xAuthToken)
            content = walletService.createTokenRequestParameters(code).encodeToParameters().formUrlEncode()
        }.andExpect {
            status { isOk() }
        }.andReturn()
        val tokenResponseParams: TokenResponseParameters = Json.decodeFromString(tokenResult.response.contentAsString)
        val accessToken = tokenResponseParams.accessToken

        val createCredentialRequest =
            runBlocking { return@runBlocking walletService.createCredentialRequest(tokenResponseParams, metadata) }
        val credentialResult = mockMvc.post("/credential") {
            header(WebSecurityConstants.X_AUTH_TOKEN, xAuthToken)
            content = Json.encodeToString(createCredentialRequest)
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

    private fun MockHttpServletRequestDsl.withOidcLoginIdToken() {
        with(oidcLogin().idToken {
            it.claim("sub", UUID.randomUUID().toString())
                .claim("birthdate", "2020-01-01")
                .claim("given_name", UUID.randomUUID().toString())
                .claim("family_name", UUID.randomUUID().toString())
        }.authorities(SimpleGrantedAuthority(AUTHORITY_OIDC)))
    }

}