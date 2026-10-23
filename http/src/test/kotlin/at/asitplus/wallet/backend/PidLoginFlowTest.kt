package at.asitplus.wallet.backend

import at.asitplus.openid.AuthenticationRequestParameters
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.openid.formUrlEncode
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.wallet.backend.config.MetadataTestPki
import at.asitplus.wallet.backend.config.buildSdJwtClaims
import at.asitplus.wallet.backend.config.walletStatusToken
import at.asitplus.wallet.backend.controller.OpenId4VpUser
import at.asitplus.wallet.lib.HttpStep
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.HolderAgent
import at.asitplus.wallet.lib.agent.IssuerAgent
import at.asitplus.wallet.lib.agent.RandomSource
import at.asitplus.wallet.lib.agent.StatusListAgent
import at.asitplus.wallet.lib.agent.toStoreCredentialInput
import at.asitplus.wallet.lib.agent.validation.StatusListTokenResolver
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.SdJwtCredentialScheme
import at.asitplus.wallet.lib.data.rfc3986.toUri
import at.asitplus.wallet.lib.openid.AuthenticationResponseResult
import at.asitplus.wallet.lib.openid.AuthorizationResponsePreparationState
import at.asitplus.wallet.lib.openid.OpenId4VpHolder
import at.asitplus.wallet.lib.openid.OpenId4VpProtocolClient
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.Url
import jakarta.servlet.http.Cookie
import java.net.URI
import java.nio.file.Files
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames
import org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
import org.springframework.session.MapSessionRepository
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get as mvcGet
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post as mvcPost
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@Import(PidLoginFlowTest.StatusConfiguration::class)
class PidLoginFlowTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var sessions: MapSessionRepository

    companion object {
        private val issuer = MetadataTestPki()
        private val statusSigner = MetadataTestPki("status-signer")
        private var statusMode = "valid"
        private val statusCertificate = certificateFile("pid-status", statusSigner)
        private val certificate = certificateFile("pid-issuer", issuer)

        private fun certificateFile(prefix: String, pki: MetadataTestPki) =
            Files.createTempFile(prefix, ".pem").also {
                Files.writeString(it, "-----BEGIN CERTIFICATE-----\n" +
                    Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(pki.intermediate.encoded) +
                    "\n-----END CERTIFICATE-----\n")
            }

        @JvmStatic @DynamicPropertySource
        fun trust(properties: DynamicPropertyRegistry) {
            properties.add("backend.trust-lists.profiles[0]") { "PID" }
            properties.add("backend.trust-lists.urls.PID[0]") { "https://127.0.0.1:1/pid.json" }
            properties.add("backend.trust-lists.trusted-certificates.PID.status[0]") { statusCertificate.toUri().toString() }
            properties.add("backend.trust-lists.trusted-certificates.PID.issuance[0]") { certificate.toUri().toString() }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class StatusConfiguration {
        @Bean @Primary
        fun pidStatusResolver() = StatusListTokenResolver { uri ->
            check(statusMode != "unavailable") { "Status unavailable" }
            walletStatusToken(
                if (statusMode == "untrusted") issuer.keyMaterial else statusSigner.keyMaterial,
                if (statusMode == "wrong-uri") "https://other.example/status" else uri.string,
                revoked = statusMode == "revoked",
                expiry = if (statusMode == "expired") Clock.System.now() - 1.days else Clock.System.now() + 1.days,
            )
        }
    }

    @BeforeEach fun resetStatus() { statusMode = "valid" }

    @ParameterizedTest
    @ValueSource(strings = ["revoked", "untrusted", "unavailable", "wrong-uri", "expired"])
    fun `invalid status cannot authenticate PID`(mode: String) = runTest {
        statusMode = mode
        val login = browserStartsPidLogin()
        walletCompletesPidAuthentication(holderWithPid(withStatus = true), login.requestUri)
        browserSeesAuthenticatedSession(login.sessionCookies, false)
    }

    @Test
    fun `authorized separate status signer permits PID login`() = runTest {
        val login = browserStartsPidLogin()
        walletCompletesPidAuthentication(holderWithPid(withStatus = true), login.requestUri)
        browserSeesAuthenticatedSession(login.sessionCookies)
    }

    @Test
    fun `wallet cancellation ends request without authenticating`() = runTest {
        val login = browserStartsPidLogin()
        val request = JwsCompactTyped<AuthenticationRequestParameters>(fetchRequest(login.requestUri)).payload
        postResponse(URI(request.responseUrl!!).rawPath, mapOf("state" to request.state!!, "error" to "access_denied").formUrlEncode())
        browserSeesAuthenticatedSession(login.sessionCookies, false)
    }

    @Test
    fun `response cannot authenticate another browser transaction`() = runTest {
        val first = browserStartsPidLogin()
        val second = browserStartsPidLogin()
        val response = holderWithPid().respondTo(fetchRequest(first.requestUri))
            .shouldBeInstanceOf<AuthenticationResponseResult.Post>()
        postResponse(URI(second.requestUri).rawPath.replace(Paths.Transaction.GetUrl, Paths.Transaction.ResultUrl), response.params.formUrlEncode(), 400)
        browserSeesAuthenticatedSession(first.sessionCookies, false)
        browserSeesAuthenticatedSession(second.sessionCookies, false)
    }

    @Test
    fun `valid proof cannot satisfy an unrequested DCQL query`() = runTest {
        val login = browserStartsPidLogin()
        val response = holderWithPid().respondTo(fetchRequest(login.requestUri))
            .shouldBeInstanceOf<AuthenticationResponseResult.Post>()
        val token = Json.parseToJsonElement(response.params.getValue("vp_token")).jsonObject
        val params = response.params + ("vp_token" to JsonObject(mapOf("unrequested" to token.values.single())).toString())
        postResponse(URI(response.url).rawPath, params.formUrlEncode())
        browserSeesAuthenticatedSession(login.sessionCookies, false)
    }

    @Test
    fun `invalid holder signature cannot authenticate`() = runTest {
        val login = browserStartsPidLogin()
        val response = holderWithPid().respondTo(fetchRequest(login.requestUri))
            .shouldBeInstanceOf<AuthenticationResponseResult.Post>()
        val token = Json.parseToJsonElement(response.params.getValue("vp_token")).jsonObject
        val entry = token.entries.single()
        val presentation = entry.value.jsonArray.single().jsonPrimitive.content
        val signatureStart = presentation.lastIndexOf('.') + 1
        val tampered = presentation.replaceRange(signatureStart, signatureStart + 1,
            if (presentation[signatureStart] == 'A') "B" else "A")
        val params = response.params + ("vp_token" to JsonObject(mapOf(
            entry.key to JsonArray(listOf(JsonPrimitive(tampered)))
        )).toString())
        postResponse(URI(response.url).rawPath, params.formUrlEncode())
        browserSeesAuthenticatedSession(login.sessionCookies, false)
    }

    @Test
    fun `processed response cannot be replayed`() = runTest {
        val login = browserStartsPidLogin()
        val response = holderWithPid().respondTo(fetchRequest(login.requestUri))
            .shouldBeInstanceOf<AuthenticationResponseResult.Post>()
        postResponse(URI(response.url).rawPath, response.params.formUrlEncode())
        postResponse(URI(response.url).rawPath, response.params.formUrlEncode(), 404)
        browserSeesAuthenticatedSession(login.sessionCookies)
    }

    @Test
    fun `untrusted PID does not authenticate`() = runTest {
        val login = browserStartsPidLogin()
        walletCompletesPidAuthentication(holderWithPid(trusted = false), login.requestUri)
        browserSeesAuthenticatedSession(login.sessionCookies, false)
    }

    @Test
    fun `expired PID does not authenticate`() = runTest {
        val login = browserStartsPidLogin()
        walletCompletesPidAuthentication(holderWithPid(expired = true), login.requestUri)
        browserSeesAuthenticatedSession(login.sessionCookies, false)
    }

    @Test
    fun `login with PID from wallet completes session login`() = runTest {
        val browserLogin = browserStartsPidLogin()

        walletCompletesPidAuthentication(holderWithPid(), browserLogin.requestUri)

        browserSeesAuthenticatedSession(browserLogin.sessionCookies)
        val session = browserSession(browserLogin.sessionCookies)
        val user = session.getAttribute<SecurityContext>(SPRING_SECURITY_CONTEXT_KEY)!!.authentication!!.principal.shouldBeInstanceOf<OpenId4VpUser>()
        user.firstname shouldBe "XXXOzgur"
        user.lastname shouldBe "XXXTuzekci"
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
        val authnRequest = fetchRequest(requestUri)

        val authnResponse = holderOid4vp.respondTo(authnRequest)
            .shouldBeInstanceOf<AuthenticationResponseResult.Post>()

        postResponse(URI(authnResponse.url).rawPath, authnResponse.params.formUrlEncode())
    }

    /** Responds to the fetched [requestObject] as a wallet does, which needs no further HTTP request to prepare. */
    private suspend fun OpenId4VpHolder.respondTo(requestObject: String): AuthenticationResponseResult {
        val prepared = OpenId4VpProtocolClient(this).prepareAuthorizationResponse(requestObject).next().getOrThrow()
            .shouldBeInstanceOf<HttpStep.Done<AuthorizationResponsePreparationState>>()
        return finalizeAuthorizationResponse(prepared.value).getOrThrow()
    }

    private fun fetchRequest(requestUri: String): String = mockMvc.perform(asyncDispatch(
        mockMvc.perform(mvcGet(URI(requestUri).rawPath)).andExpect(request().asyncStarted()).andReturn()
    )).andExpect(status().isOk).andExpect(content().contentTypeCompatibleWith("application/oauth-authz-req+jwt"))
        .andReturn().response.contentAsString

    private fun postResponse(path: String, body: String, expectedStatus: Int = 200) {
        val result = mockMvc.perform(asyncDispatch(
            mockMvc.perform(mvcPost(path).contentType(MediaType.APPLICATION_FORM_URLENCODED).content(body))
                .andExpect(request().asyncStarted()).andReturn()
        )).andExpect(status().`is`(expectedStatus))
        if (expectedStatus == 200) result.andExpect(content().json("{}"))
            .andExpect(header().string("Cache-Control", "no-store"))
    }

    private fun browserSeesAuthenticatedSession(sessionCookies: Array<Cookie>, authenticated: Boolean = true) {
        mockMvc.perform(asyncDispatch(
            mockMvc.perform(mvcGet(Paths.LoginStatusUrl).cookie(*sessionCookies))
                .andExpect(request().asyncStarted())
                .andReturn()
        ))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(authenticated))
        if (!authenticated) {
            val session = browserSession(sessionCookies)
            session.getAttribute<SecurityContext>(SPRING_SECURITY_CONTEXT_KEY) shouldBe null
        }
    }

    private fun browserSession(cookies: Array<Cookie>) = sessions.findById(
        String(Base64.getDecoder().decode(cookies.single { it.name == "SESSION" }.value))
    ).shouldNotBeNull()

    private data class BrowserLogin(
        val sessionCookies: Array<Cookie>,
        val requestUri: String,
    )

    private suspend fun holderWithPid(trusted: Boolean = true, expired: Boolean = false, withStatus: Boolean = false): OpenId4VpHolder {
        val holderKey = EphemeralKeyWithoutCert()
        val holder = HolderAgent(holderKey)
        val now = Clock.System.now()
        holder.storeCredential(
            IssuerAgent(
                keyMaterial = if (trusted) issuer.keyMaterial else EphemeralKeyWithoutCert(),
                statusListAgent = if (withStatus) StatusListAgent(keyMaterial = statusSigner.keyMaterial, statusListBaseUrl = "https://issuer.example.com/status") else null,
                identifier = "https://issuer.example.com/".toUri(),
                randomSource = RandomSource.Default,
            ).issueCredential(
                pidSdJwtScheme().buildSdJwtClaims(
                    userInfo = pidUserInfo(),
                    iss = if (expired) now - 2.days else now,
                    exp = if (expired) now - 1.days else now + 1.days,
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
