package at.asitplus.wallet.backend

import at.asitplus.catching
import at.asitplus.iso.IssuerSigned
import at.asitplus.openid.CredentialResponseParameters
import at.asitplus.openid.TokenResponseParameters
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.signum.indispensable.josef.JsonWebToken
import at.asitplus.signum.indispensable.josef.JwsSigned
import at.asitplus.wallet.backend.auth.SpringSecurityAuthenticationSupplier.toOidcUserInfoExtended
import at.asitplus.wallet.backend.data.OidcIssuerCredentialDataProvider
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.SdJwtDecoded
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.*
import at.asitplus.wallet.lib.data.VerifiableCredentialSdJwt
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.lib.jws.JwsHeaderCertOrJwk
import at.asitplus.wallet.lib.jws.SdJwtSigned
import at.asitplus.wallet.lib.jws.SignJwt
import at.asitplus.wallet.lib.jws.SignJwtFun
import at.asitplus.wallet.lib.oauth2.OAuth2Client
import at.asitplus.wallet.lib.oauth2.RequestInfo
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.BuildDPoPHeader
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.WalletService.RequestOptions
import at.asitplus.wallet.lib.openid.AuthenticationResponseResult
import com.benasher44.uuid.uuid4
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.test.context.support.WithSecurityContext
import org.springframework.security.test.context.support.WithSecurityContextFactory
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * Tests the issuing process
 * by facilitating the internal authorization server,
 * and using Spring Security to extract user data, see [WithOAuth2AuthenticationToken]
 */
@OptIn(ExperimentalSerializationApi::class)
@SpringBootTest
class SpringBootSecurityIssuingTest {

    @Autowired
    private lateinit var credentialIssuer: CredentialIssuer

    @Autowired
    private lateinit var authorizationServer: SimpleAuthorizationService

    @Test
    @WithOAuth2AuthenticationToken
    fun pid_new_sdjwt_ok() = runTest {
        val requestOptions = RequestOptions(sdJwtScheme("urn:eudi:pid:1"), SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        joseCompliantSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString())
            .disclosureDigests.shouldNotBeNull().size shouldBeGreaterThan 1
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun pid_iso_ok() = runTest {
        val requestOptions = RequestOptions(isoScheme("eu.europa.ec.eudi.pid.1"), ISO_MDOC)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        coseCompliantSerializer.decodeFromByteArray<IssuerSigned>(serializedCredential.decodeToByteArray(Base64()))
            .namespaces?.values?.firstOrNull()
            ?.entries?.size.shouldNotBeNull() shouldBeGreaterThan 1
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun cor_sdjwt_ok() = runTest {
        val requestOptions = RequestOptions(sdJwtScheme("eu.europa.ec.eudi.cor.1"), SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        joseCompliantSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString())
            .disclosureDigests.shouldNotBeNull().size shouldBeGreaterThan 1
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun por_sdjwt_ok() = runTest {
        val requestOptions = RequestOptions(sdJwtScheme("urn:eu.europa.ec.eudi:por:1"), SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        joseCompliantSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString()).apply {
            subject.shouldNotBeNull()
            disclosureDigests.shouldBeNull()
        }
        SdJwtDecoded(SdJwtSigned.parseCatching(serializedCredential).getOrThrow())
            .reconstructedJsonObject.shouldNotBeNull()
            .keys.shouldContain("issuing_authority")
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun ehic_ok() = runTest {
        val requestOptions = RequestOptions(sdJwtScheme("urn:eudi:ehic:1"), SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        joseCompliantSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString()).apply {
            subject.shouldNotBeNull()
            disclosureDigests.shouldBeNull()
        }
        SdJwtDecoded(SdJwtSigned.parseCatching(serializedCredential).getOrThrow())
            .reconstructedJsonObject.shouldNotBeNull()
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun taxid_ok() = runTest {
        val requestOptions = RequestOptions(sdJwtScheme("urn:eu.europa.ec.eudi:tax:1"), SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        joseCompliantSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString()).apply {
            subject.shouldNotBeNull()
            disclosureDigests.shouldBeNull()
        }
        SdJwtDecoded(
            SdJwtSigned.parseCatching(serializedCredential).getOrThrow()
        ).reconstructedJsonObject.shouldNotBeNull()
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun mdl_iso_ok() = runTest {
        val requestOptions = RequestOptions(isoScheme("org.iso.18013.5.1.mDL"), ISO_MDOC)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        coseCompliantSerializer.decodeFromByteArray<IssuerSigned>(serializedCredential.decodeToByteArray(Base64()))
            .namespaces?.values?.firstOrNull()
            ?.entries?.size.shouldNotBeNull() shouldBeGreaterThan 1
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun age_iso_ok() = runTest {
        val requestOptions = RequestOptions(isoScheme("eu.europa.ec.av.1"), ISO_MDOC)
        val client = Client()
        val supportedCredentialConfigurations = credentialIssuer.metadata.supportedCredentialConfigurations.shouldNotBeNull()
        val credentialFormat = client.oid4vciClient
            .selectSupportedCredentialFormat(requestOptions, credentialIssuer.metadata)
            .shouldNotBeNull()
        val credentialConfigurationId = supportedCredentialConfigurations.entries
            .firstOrNull { it.value == credentialFormat }
            ?.key
            .shouldNotBeNull()

        credentialConfigurationId shouldBe "proof_of_age"
        supportedCredentialConfigurations[credentialConfigurationId].shouldNotBeNull().scope shouldBe "proof_of_age"
        credentialFormat.scope shouldBe "proof_of_age"

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        coseCompliantSerializer.decodeFromByteArray<IssuerSigned>(serializedCredential.decodeToByteArray(Base64()))
            .namespaces?.values?.firstOrNull()
            ?.entries?.size.shouldNotBeNull() shouldBeGreaterThan 1
    }

    // Schemes are resolved from remote type metadata and registered at boot.
    private fun sdJwtScheme(vct: String) =
        AttributeIndex.resolveSdJwtAttributeType(vct) ?: error("SD-JWT scheme not resolved: $vct")

    private fun isoScheme(docType: String) =
        AttributeIndex.resolveIsoDoctype(docType) ?: error("ISO mdoc scheme not resolved: $docType")

    private suspend fun loadCredential(requestOptions: RequestOptions): CredentialResponseParameters {
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
                toOidcUserInfoExtended(SecurityContextHolder.getContext()?.authentication)
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
        val credential = credentialIssuer.credential(
            authorizationHeader = accessToken.toHttpHeaderValue(),
            params = credentialRequest.first(),
            credentialDataProvider = OidcIssuerCredentialDataProvider(
                lifetime = 1.minutes,
            ),
        ).getOrThrow()
        return credential
            .shouldBeInstanceOf<CredentialIssuer.CredentialResponse.Plain>()
            .response
    }

}

/**
 * Gives us full flexibility to insert a fake [OAuth2AuthenticationToken] into the security context of the unit test.
 *
 * Data from <https://eid.egiz.gv.at/template/examples/idToken.json>
 */
class WithOAuth2AuthenticationTokenSecurityContextFactory : WithSecurityContextFactory<WithOAuth2AuthenticationToken> {
    override fun createSecurityContext(customUser: WithOAuth2AuthenticationToken): SecurityContext {
        val context = SecurityContextHolder.createEmptyContext()
        val authorities = AuthorityUtils.createAuthorityList("notimportant")
        val principal = DefaultOidcUser(authorities, mockOidcIdToken())
        val authentication = OAuth2AuthenticationToken(principal, authorities, "clientId")
        context.authentication = authentication
        return context
    }
}

private fun mockOidcIdToken(): OidcIdToken = OidcIdToken(
    /* tokenValue = */ "tokenValue",
    /* issuedAt = */ Instant.now().minusSeconds(10),
    /* expiresAt = */ Instant.now().plusSeconds(10),
    /* claims = */ mapOf(
        IdTokenClaimNames.SUB to "IFOQP3T5XYLMSDOQAEGMF52MWGMWBPXN",
        "birthdate" to "1983-06-04",
        "given_name" to "XXXŐzgür",
        "family_name" to "XXXTüzekçi",
        "urn:pvpgvat:oidc.bpk" to "ZP-MH:KQMY8Sl9WsmBxrYrYOiFS2VkLyo=",
    )
)

@Retention(AnnotationRetention.RUNTIME)
@WithSecurityContext(factory = WithOAuth2AuthenticationTokenSecurityContextFactory::class)
annotation class WithOAuth2AuthenticationToken
