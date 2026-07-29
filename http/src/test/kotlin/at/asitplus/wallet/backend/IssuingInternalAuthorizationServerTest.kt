package at.asitplus.wallet.backend


import at.asitplus.iso.IssuerSigned
import at.asitplus.openid.CredentialResponseParameters
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.openid.TokenResponseParameters
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.signum.indispensable.josef.JsonWebToken
import at.asitplus.signum.indispensable.josef.JwsTyped
import at.asitplus.wallet.backend.config.Ida15BindingClaims
import at.asitplus.wallet.backend.data.OidcIssuerCredentialDataProvider
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.SdJwtDecoded
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.ISO_MDOC
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.SD_JWT
import at.asitplus.wallet.lib.data.VerifiableCredentialSdJwt
import at.asitplus.wallet.lib.jws.JwsHeaderCertOrJwk
import at.asitplus.wallet.lib.jws.SdJwtSigned
import at.asitplus.wallet.lib.jws.SignJwt
import at.asitplus.wallet.lib.jws.SignJwtFun
import at.asitplus.wallet.lib.oauth2.OAuth2Client
import at.asitplus.wallet.lib.oauth2.RequestInfo
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.BuildDPoPHeader
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.WalletService
import at.asitplus.wallet.lib.openid.AuthenticationResponseResult
import com.benasher44.uuid.uuid4
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames
import kotlin.time.Duration.Companion.minutes

/**
 * Tests the issuing process,
 * by facilitating the internal authorization server,
 * and not relying on Spring Security to authorize the user.
 */
@Suppress("OPT_IN_USAGE")
@SpringBootTest
class IssuingInternalAuthorizationServerTest {

    @Autowired
    private lateinit var credentialIssuer: CredentialIssuer

    @Autowired
    private lateinit var authorizationServer: SimpleAuthorizationService

    @Test
    fun pid_new_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(sdJwtScheme("urn:eudi:pid:1"), SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        JwsTyped<VerifiableCredentialSdJwt>(serializedCredential.substringBefore("~")).payload
            .disclosureDigests.shouldNotBeNull().size shouldBeGreaterThan 1
    }

    @Test
    fun pid_iso_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(isoScheme("eu.europa.ec.eudi.pid.1"), ISO_MDOC)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        coseCompliantSerializer.decodeFromByteArray<IssuerSigned>(serializedCredential.decodeToByteArray(Base64()))
            .apply {
                namespaces?.values?.firstOrNull()?.entries?.size.shouldNotBeNull() shouldBeGreaterThan 1
                issuerAuth.payload.shouldNotBeNull().status.shouldNotBeNull()
            }
    }

    @Test
    fun cor_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(sdJwtScheme("eu.europa.ec.eudi.cor.1"), SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        JwsTyped<VerifiableCredentialSdJwt>(serializedCredential.substringBefore("~")).payload
            .disclosureDigests.shouldNotBeNull().size shouldBeGreaterThan 1
    }

    @Test
    fun por_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(sdJwtScheme("urn:eu.europa.ec.eudi:por:1"), SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        JwsTyped<VerifiableCredentialSdJwt>(serializedCredential.substringBefore("~")).payload.apply {
            subject.shouldNotBeNull()
            disclosureDigests.shouldBeNull()
            statusElement.shouldNotBeNull()
        }
        SdJwtDecoded(
            SdJwtSigned.parseCatching(serializedCredential).getOrThrow()
        ).reconstructedJsonObject.shouldNotBeNull()
            .keys.shouldContain("issuing_authority")
    }

    @Test
    fun ehic_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(sdJwtScheme("urn:eudi:ehic:1"), SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        JwsTyped<VerifiableCredentialSdJwt>(serializedCredential.substringBefore("~")).payload.apply {
            issuer.shouldNotBeNull()
            disclosureDigests.shouldBeNull()
            statusElement.shouldNotBeNull()
        }
        SdJwtDecoded(
            SdJwtSigned.parseCatching(serializedCredential).getOrThrow()
        ).reconstructedJsonObject.shouldNotBeNull()
    }

    @Test
    fun taxid_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(sdJwtScheme("urn:eu.europa.ec.eudi:tax:1"), SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        JwsTyped<VerifiableCredentialSdJwt>(serializedCredential.substringBefore("~")).payload.apply {
            issuer.shouldNotBeNull()
            disclosureDigests.shouldBeNull()
            statusElement.shouldNotBeNull()
        }
        SdJwtDecoded(
            SdJwtSigned.parseCatching(serializedCredential).getOrThrow()
        ).reconstructedJsonObject.shouldNotBeNull()
    }

    @Test
    fun ida15_binding_ok() = runTest {
        val credential = loadCredential(
            WalletService.RequestOptions(sdJwtScheme(Ida15BindingClaims.VCT), SD_JWT)
        )

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        JwsTyped<VerifiableCredentialSdJwt>(serializedCredential.substringBefore("~")).payload
            .disclosureDigests.shouldBeNull()
        val claims = SdJwtDecoded(
            SdJwtSigned.parseCatching(serializedCredential).getOrThrow()
        ).reconstructedJsonObject.shouldNotBeNull()
        claims.apply {
            keys.shouldContain(Ida15BindingClaims.SIGNER_CERTIFICATE)
            keys.shouldContain(Ida15BindingClaims.IDENTITY_TYPE)
            keys.shouldContain(Ida15BindingClaims.ISSUING_COUNTRY)
            keys.shouldContain(Ida15BindingClaims.EID_STATUS)
            keys.shouldContain(Ida15BindingClaims.VSZ_SHA256)
        }
    }

    @Test
    fun mdl_iso_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(isoScheme("org.iso.18013.5.1.mDL"), ISO_MDOC)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        coseCompliantSerializer.decodeFromByteArray<IssuerSigned>(serializedCredential.decodeToByteArray(Base64()))
            .namespaces?.values?.firstOrNull()
            ?.entries?.size.shouldNotBeNull() shouldBeGreaterThan 1
    }

    @Test
    fun age_iso_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(isoScheme("eu.europa.ec.av.1"), ISO_MDOC)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        coseCompliantSerializer.decodeFromByteArray<IssuerSigned>(serializedCredential.decodeToByteArray(Base64()))
            .namespaces?.values?.firstOrNull()
            ?.entries?.size.shouldNotBeNull() shouldBeGreaterThan 1
    }

    // Schemes are resolved from type metadata and registered at boot.
    private fun sdJwtScheme(vct: String) =
        AttributeIndex.resolveSdJwtAttributeType(vct) ?: error("SD-JWT scheme not resolved: $vct")

    private fun isoScheme(docType: String) =
        AttributeIndex.resolveIsoDoctype(docType) ?: error("ISO mdoc scheme not resolved: $docType")

    private suspend fun loadCredential(requestOptions: WalletService.RequestOptions): CredentialResponseParameters {
        val client = Client()
        val signDpop: SignJwtFun<JsonWebToken> = SignJwt(EphemeralKeyWithoutCert(), JwsHeaderCertOrJwk())
        val state = uuid4().toString()
        val credentialFormat = client.oid4vciClient
            .selectSupportedCredentialFormat(requestOptions, credentialIssuer.metadata)
            .shouldNotBeNull()
        val scope = credentialFormat.scope
        val authnRequest = client.oauth2Client.createAuthRequest(state, authorizationDetails = null, scope = scope)
        val authorizationCode = authorizationServer.authorize(authnRequest) { mockOidcUserInfoExtended() }.getOrThrow()
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

private fun mockOidcUserInfoExtended() = OidcUserInfoExtended.fromJsonObject(buildJsonObject {
    put(IdTokenClaimNames.SUB, "IFOQP3T5XYLMSDOQAEGMF52MWGMWBPXN")
    put("birthdate", "1983-06-04")
    put("given_name", "XXXŐzgür")
    put("family_name", "XXXTüzekçi")
    put("urn:pvpgvat:oidc.bpk", "ZP-MH:KQMY8Sl9WsmBxrYrYOiFS2VkLyo=")
    put("urn:pvpgvat:oidc.eid_signer_certificate", "fake-value")
})
