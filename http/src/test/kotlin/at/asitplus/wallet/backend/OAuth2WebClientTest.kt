package at.asitplus.wallet.backend

import at.asitplus.openid.OidcUserInfo
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.signum.indispensable.josef.JsonWebToken
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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.http.HttpHeaders as KtorHttpHeaders
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT1M")
class OAuth2WebClientTest {

    @Autowired
    private lateinit var webClient: WebTestClient

    @Autowired
    private lateinit var credentialIssuer: CredentialIssuer

    @Autowired
    private lateinit var authorizationServer: SimpleAuthorizationService

    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `pre-authorized token error response includes DPoP nonce for WebTestClient`() = runTest {
        val oauth2Client = OAuth2Client()
        val credentialOffer = authorizationServer.offerWithPreAuthnForUserForSchemes(
            user = OidcUserInfoExtended.fromOidcUserInfo(OidcUserInfo(subject = "web-test-user")).getOrThrow(),
            credentialIssuer = credentialIssuer.metadata.credentialIssuer,
            schemes = emptySet(),
        )
        val preAuthCode = credentialOffer.grants?.preAuthorizedCode?.preAuthorizedCode.shouldNotBeNull()
        val tokenRequest = oauth2Client.createTokenRequestParameters(
            authorization = OAuth2Client.AuthorizationForToken.PreAuthCode(preAuthCode),
        )
        val tokenUrl = "http://localhost:$port${Paths.TokenUrl}"
        val dpop = BuildDPoPHeader(
            signDpop = SignJwt<JsonWebToken>(EphemeralKeyWithoutCert(), JwsHeaderCertOrJwk()),
            url = tokenUrl,
        )

        webClient.post().uri(Paths.TokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .header(KtorHttpHeaders.DPoP, dpop)
            .bodyValue(tokenRequest.encodeToParameters().formUrlEncode())
            .exchange()
            .expectStatus().isBadRequest
            .expectHeader().valueMatches(KtorHttpHeaders.DPoPNonce, ".+")
    }
}
