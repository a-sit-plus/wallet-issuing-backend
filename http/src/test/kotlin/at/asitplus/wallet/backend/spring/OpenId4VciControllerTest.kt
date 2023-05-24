package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.ProfileConstants
import at.asitplus.wallet.backend.auth.AuthenticatedDeviceBindingUser
import at.asitplus.wallet.backend.auth.WebSecurityConstants.AUTHORITY_OIDC
import at.asitplus.wallet.lib.oidvci.WalletService
import at.asitplus.wallet.lib.oidvci.encodeToParameters
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
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
@AutoConfigureMockMvc
@ActiveProfiles(ProfileConstants.EIDASID)
class OpenId4VciControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private lateinit var clientRedirectUrl: String
    private lateinit var walletService: WalletService

    @BeforeEach
    fun beforeEach() {
        clientRedirectUrl = "https://localhost/${UUID.randomUUID()}"
        walletService = WalletService(
            tokenType = arrayOf("IdAustriaCredential"),
            clientId = "https://wallet.a-sit.at/mobile",
            redirectUrl = clientRedirectUrl,
        )
    }

    @Test
    fun metadata_unauthenticated() {
        mockMvc.get("/.well-known/openid-credential-issuer")
            .andExpect { status { isOk() } }
            .andReturn()
    }

    @Test
    fun authorize_unauthenticated() {
        mockMvc.post("/authorize") {
            walletService.createAuthRequest().encodeToParameters().forEach {
                this.param(it.key, it.value)
            }
        }.andExpect { status { isForbidden() } }
            .andReturn()
    }

    @Test
    fun token_unauthenticated() {
        mockMvc.post("/token") {
            content = walletService.createTokenRequestParameters(UUID.randomUUID().toString()).encodeToParameters()
        }.andExpect { status { isForbidden() } }
            .andReturn()
    }

    @Test
    fun credential_unauthenticated() {
        mockMvc.post("/credential") {
            content = walletService.createAuthRequest().encodeToParameters()
            header(HttpHeaders.AUTHORIZATION, "Bearer foo")
        }.andExpect { status { isForbidden() } }
            .andReturn()
    }

    @Test
    fun authorize_with_oidc_login() {
        mockMvc.post("/authorize") {
            withOidcLoginIdToken()
            walletService.createAuthRequest().encodeToParameters().forEach {
                this.param(it.key, it.value)
            }
        }.andExpect { status { isForbidden() } }
            .andReturn()
    }

    @Test
    fun authorize_success_with_device_binding() {
        val dummyUser = AuthenticatedDeviceBindingUser(UUID.randomUUID().toString(), Random.nextBytes(32))
        val result = mockMvc.post("/authorize") {
            with(user(dummyUser))
            walletService.createAuthRequest().encodeToParameters().forEach {
                this.param(it.key, it.value)
            }
        }.andExpect { status { is3xxRedirection() } }
            // Redirect means success, as the client gets an authorization code back
            .andReturn()

        val redirectUrl = result.response.getHeader(HttpHeaders.LOCATION)
        redirectUrl shouldStartWith clientRedirectUrl
        redirectUrl shouldContain "code="
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