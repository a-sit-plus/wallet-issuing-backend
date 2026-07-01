package at.asitplus.wallet.backend

import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.wallet.backend.config.buildSdJwtClaims
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.HolderAgent
import at.asitplus.wallet.lib.agent.IssuerAgent
import at.asitplus.wallet.lib.agent.RandomSource
import at.asitplus.wallet.lib.agent.toStoreCredentialInput
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.SdJwtCredentialScheme
import at.asitplus.wallet.lib.data.rfc3986.toUri
import at.asitplus.wallet.lib.oidvci.formUrlEncode
import at.asitplus.wallet.lib.openid.AuthenticationResponseResult
import at.asitplus.wallet.lib.openid.OpenId4VpHolder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.Url
import jakarta.servlet.http.Cookie
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get as mvcGet
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post as mvcPost
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.net.URI
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@SpringBootTest
@AutoConfigureMockMvc
class PidLoginFlowTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `login with PID from wallet completes session login`() = runTest {
        val browserLogin = browserStartsPidLogin()

        walletCompletesPidAuthentication(holderWithPid(), browserLogin.requestUri)

        browserSeesAuthenticatedSession(browserLogin.sessionCookies)
    }

    private fun browserStartsPidLogin(): BrowserLogin {
        val loginResult = mockMvc.perform(asyncDispatch(
            mockMvc.get(Paths.LoginUrl)
                .andExpect { request { asyncStarted() } }
                .andReturn()
        ))
            .andExpect(status().isOk)
            .andReturn()
        val sessionCookies = loginResult.response.cookies
        sessionCookies.size shouldNotBe 0
        val loginPidUrl = loginResult.modelAndView?.model?.get("loginPidUrl")
            .shouldBeInstanceOf<String>()
        return BrowserLogin(
            sessionCookies = sessionCookies,
            requestUri = Url(loginPidUrl).parameters["request_uri"].shouldNotBeNull(),
        )
    }

    private suspend fun walletCompletesPidAuthentication(holderOid4vp: OpenId4VpHolder, requestUri: String) {
        val authnRequest = mockMvc.perform(asyncDispatch(
            mockMvc.perform(mvcGet(URI(requestUri).rawPath))
                .andExpect(request().asyncStarted())
                .andReturn()
        ))
            .andExpect(status().isOk)
            .andReturn()
            .response.contentAsString

        val authnResponse = holderOid4vp.createAuthnResponse(authnRequest)
            .getOrThrow()
            .shouldBeInstanceOf<AuthenticationResponseResult.Post>()

        mockMvc.perform(asyncDispatch(
            mockMvc.perform(
                mvcPost(URI(authnResponse.url).rawPath)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .content(authnResponse.params.formUrlEncode())
            )
                .andExpect(request().asyncStarted())
                .andReturn()
        ))
            .andExpect(status().isOk)
    }

    private fun browserSeesAuthenticatedSession(sessionCookies: Array<Cookie>) {
        mockMvc.perform(asyncDispatch(
            mockMvc.perform(mvcGet(Paths.LoginStatusUrl).cookie(*sessionCookies))
                .andExpect(request().asyncStarted())
                .andReturn()
        ))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(true))
    }

    private data class BrowserLogin(
        val sessionCookies: Array<Cookie>,
        val requestUri: String,
    )

    private suspend fun holderWithPid(): OpenId4VpHolder {
        val holderKey = EphemeralKeyWithoutCert()
        val holder = HolderAgent(holderKey)
        val now = Clock.System.now()
        holder.storeCredential(
            IssuerAgent(
                identifier = "https://issuer.example.com/".toUri(),
                randomSource = RandomSource.Default,
            ).issueCredential(
                pidSdJwtScheme().buildSdJwtClaims(
                    userInfo = pidUserInfo(),
                    iss = now,
                    exp = now + 1.days,
                    subjectPublicKey = holderKey.publicKey,
                )
            ).getOrThrow().toStoreCredentialInput()
        ).getOrThrow()
        return OpenId4VpHolder(holder = holder, randomSource = RandomSource.Default)
    }

    // Scheme is resolved from remote type metadata registered at boot, not the removed library scheme object.
    private fun pidSdJwtScheme() =
        AttributeIndex.resolveSdJwtAttributeType("urn:eudi:pid:1") as? SdJwtCredentialScheme
            ?: error("SD-JWT scheme not resolved: urn:eudi:pid:1")

    private fun pidUserInfo() = OidcUserInfoExtended.fromJsonObject(buildJsonObject {
        put(IdTokenClaimNames.SUB, "IFOQP3T5XYLMSDOQAEGMF52MWGMWBPXN")
        put("birthdate", "1983-06-04")
        put("given_name", "XXXOzgur")
        put("family_name", "XXXTuzekci")
    }).getOrThrow()
}
