package at.asitplus.wallet.backend

import at.asitplus.openid.AttestationChallengeResponse
import at.asitplus.openid.OAuth2AuthorizationServerMetadata
import at.asitplus.openid.encodeToParameters
import at.asitplus.openid.formUrlEncode
import at.asitplus.signum.indispensable.josef.ClientStatus
import at.asitplus.signum.indispensable.josef.JsonWebToken
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.backend.config.walletStatusToken
import at.asitplus.wallet.lib.agent.validation.StatusListTokenResolver
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Import
import at.asitplus.wallet.backend.config.MetadataTestPki
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.jws.JwsHeaderCertOrJwk
import at.asitplus.wallet.lib.jws.JwsHeaderNone
import at.asitplus.wallet.lib.jws.SignJwt
import at.asitplus.wallet.lib.oauth2.OAuth2Client
import at.asitplus.wallet.lib.oauth2.OAuthClientAttestation
import at.asitplus.wallet.lib.oauth2.OAuthClientAttestationChallenge
import at.asitplus.wallet.lib.oauth2.OAuthClientAttestationPop
import at.asitplus.wallet.lib.oidvci.BuildClientAttestationJwt
import at.asitplus.wallet.lib.oidvci.BuildClientAttestationPoPJwt
import at.asitplus.wallet.lib.oidvci.OpenId4VciServer
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@SpringBootTest
@AutoConfigureMockMvc
@Import(WalletAttestationChallengeTest.StatusConfiguration::class)
class WalletAttestationChallengeTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var configuration: BackendConfigurationProperties
    @Autowired lateinit var credentialIssuer: OpenId4VciServer

    @Test
    fun `authorization server advertises the challenge endpoint`() {
        val metadata = joseCompliantSerializer.decodeFromString<OAuth2AuthorizationServerMetadata>(
            mockMvc.perform(asyncDispatch(mockMvc.get("/.well-known/oauth-authorization-server")
                .andExpect { request { asyncStarted() } }.andReturn()))
                .andExpect(status().isOk).andReturn().response.contentAsString
        )
        metadata.challengeEndpoint shouldBe "${configuration.publicContext}${Paths.ChallengeUrl}"
    }

    @Test
    fun `PAR asks for an attestation challenge and accepts a PoP answering it`() = runBlocking {
        val client = OAuth2Client()
        val clientKey = EphemeralKeyWithSelfSignedCert()
        val attestation = BuildClientAttestationJwt(
            SignJwt(walletProvider, JwsHeaderCertOrJwk()),
            clientId = client.clientId,
            clientKey = clientKey.jsonWebKey,
            // walletStatusResolver serves a list for any URI, whose entry at idx 0 reflects revoked
            clientStatus = ClientStatus(
                status = buildJsonObject {
                    putJsonObject("status_list") {
                        put("idx", 0)
                        put("uri", "https://example.org/status/wallet-instance")
                    }
                },
                expiration = Clock.System.now() + 31.days,
            ),
        )
        val scope = credentialIssuer.metadata.supportedCredentialConfigurations!!.values.first().scope!!
        suspend fun par(challenge: String?): ResultActions {
            val pop = BuildClientAttestationPoPJwt(
                SignJwt(clientKey, JwsHeaderNone()),
                audience = configuration.publicContext.toString(),
                nonce = challenge,
            )
            val authRequest = client.createAuthRequest(state = "state", scope = scope)
            return mockMvc.perform(asyncDispatch(mockMvc.post(Paths.ParUrl) {
                contentType = MediaType.APPLICATION_FORM_URLENCODED
                header(HttpHeaders.OAuthClientAttestation, attestation.toString())
                header(HttpHeaders.OAuthClientAttestationPop, pop.toString())
                content = authRequest.encodeToParameters().formUrlEncode()
            }.andExpect { request { asyncStarted() } }.andReturn()))
        }

        val rejected: MvcResult = par(challenge = null)
            .andExpect(status().isBadRequest)
            .andExpect(content().string(containsString("use_attestation_challenge")))
            .andReturn()
        rejected.response.getHeader(HttpHeaders.OAuthClientAttestationChallenge).shouldNotBeNull()

        val challenge = joseCompliantSerializer.decodeFromString<AttestationChallengeResponse>(
            mockMvc.perform(asyncDispatch(mockMvc.post(Paths.ChallengeUrl)
                .andExpect { request { asyncStarted() } }.andReturn()))
                .andExpect(status().isOk)
                .andExpect(header().string(HttpHeaders.CacheControl, "no-store"))
                .andReturn().response.contentAsString
        ).attestationChallenge.shouldNotBeNull()

        val accepted = par(challenge).andExpect(status().isCreated)
            .andExpect(header().exists(HttpHeaders.OAuthClientAttestationChallenge)).andReturn()
        revoked = true
        try {
            par(accepted.response.getHeader(HttpHeaders.OAuthClientAttestationChallenge))
                .andExpect(status().isBadRequest)
                .andExpect(content().string(containsString("client_status invalid")))
        } finally { revoked = false }
        Unit
    }

    @TestConfiguration
    class StatusConfiguration {
        @Bean
        @Primary
        fun walletStatusResolver() = StatusListTokenResolver { walletStatusToken(walletProvider, it.string, revoked) }
    }

    companion object {
        private var revoked = false
        private val providerPki = MetadataTestPki()
        private val walletProvider = providerPki.keyMaterial

        @JvmStatic
        @DynamicPropertySource
        fun walletAttestation(registry: DynamicPropertyRegistry) {
            val der = providerPki.intermediate.encoded
            val pem = Files.createTempFile("wallet-provider", ".pem").apply {
                toFile().deleteOnExit()
                Files.writeString(
                    this,
                    "-----BEGIN CERTIFICATE-----\n" +
                            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) +
                            "\n-----END CERTIFICATE-----\n"
                )
            }
            registry.add("backend.trust-lists.trusted-certificates.WALLET.status[0]") { pem.toUri().toString() }
            registry.add("backend.trust-lists.profiles[0]") { "WALLET" }
            registry.add("backend.wallet-attestation.enabled") { true }
            registry.add("backend.wallet-attestation.trusted-certificates") { pem.toUri().toString() }
            // Fail fast without network access; the configured issuing CA authorizes this provider.
            registry.add("backend.wallet-attestation.wallet-provider-lote-urls") { "https://127.0.0.1:1/lote.json" }
        }
    }
}
