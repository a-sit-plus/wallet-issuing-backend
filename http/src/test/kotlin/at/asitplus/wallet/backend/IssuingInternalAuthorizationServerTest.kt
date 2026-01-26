package at.asitplus.wallet.backend


import at.asitplus.iso.IssuerSigned
import at.asitplus.iso.IssuerSignedList
import at.asitplus.openid.CredentialResponseParameters
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.openid.TokenResponseParameters
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.signum.indispensable.josef.JsonWebToken
import at.asitplus.signum.indispensable.josef.JwsSigned
import at.asitplus.wallet.ageverification.AgeVerificationScheme
import at.asitplus.wallet.backend.data.OidcIssuerCredentialDataProvider
import at.asitplus.wallet.cor.CertificateOfResidenceScheme
import at.asitplus.wallet.ehic.EhicScheme
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtScheme
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.SdJwtDecoded
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.*
import at.asitplus.wallet.lib.data.VerifiableCredentialJws
import at.asitplus.wallet.lib.data.VerifiableCredentialSdJwt
import at.asitplus.wallet.lib.data.vckJsonSerializer
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
import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
import at.asitplus.wallet.por.PowerOfRepresentationDataElements
import at.asitplus.wallet.por.PowerOfRepresentationScheme
import at.asitplus.wallet.taxid.TaxIdScheme
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
import kotlinx.serialization.Contextual
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
    fun pid_vc_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(EuPidScheme, PLAIN_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential).getOrThrow()
        val vcJws = vckJsonSerializer.decodeFromString<VerifiableCredentialJws>(jws.payload.decodeToString())
        vcJws.vc.credentialSubject.shouldBeInstanceOf<EuPidCredential>()
            .birthDate shouldBe LocalDate(1983, 6, 4)
    }

    @Test
    fun pid_new_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(EuPidSdJwtScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        vckJsonSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString())
            .disclosureDigests.shouldNotBeNull().size shouldBeGreaterThan 1
    }

    @Test
    fun pid_iso_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(EuPidScheme, ISO_MDOC)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        coseCompliantSerializer.decodeFromByteArray<IssuerSigned>(serializedCredential.decodeToByteArray(Base64()))
            .namespaces?.values?.firstOrNull<@Contextual IssuerSignedList>()?.entries?.size.shouldNotBeNull()
            .shouldBeGreaterThan(1)
    }

    @Test
    fun cor_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(CertificateOfResidenceScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        vckJsonSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString())
            .disclosureDigests.shouldNotBeNull().size shouldBeGreaterThan 1
    }

    @Test
    fun por_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(PowerOfRepresentationScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        vckJsonSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString()).apply {
            subject.shouldNotBeNull()
            disclosureDigests.shouldBeNull()
        }
        SdJwtDecoded(
            SdJwtSigned.parseCatching(serializedCredential).getOrThrow()
        ).reconstructedJsonObject.shouldNotBeNull()
            .keys.shouldContain(PowerOfRepresentationDataElements.ISSUING_AUTHORITY)
    }

    @Test
    fun ehic_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(EhicScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        vckJsonSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString()).apply {
            issuer.shouldNotBeNull()
            disclosureDigests.shouldBeNull()
        }
        SdJwtDecoded(
            SdJwtSigned.parseCatching(serializedCredential).getOrThrow()
        ).reconstructedJsonObject.shouldNotBeNull()
    }

    @Test
    fun taxid_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(TaxIdScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        vckJsonSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString()).apply {
            issuer.shouldNotBeNull()
            disclosureDigests.shouldBeNull()
        }
        SdJwtDecoded(
            SdJwtSigned.parseCatching(serializedCredential).getOrThrow()
        ).reconstructedJsonObject.shouldNotBeNull()
    }

    @Test
    fun mdl_iso_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(MobileDrivingLicenceScheme, ISO_MDOC)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        coseCompliantSerializer.decodeFromByteArray<IssuerSigned>(serializedCredential.decodeToByteArray(Base64()))
            .namespaces?.values?.firstOrNull()
            ?.entries?.size.shouldNotBeNull() shouldBeGreaterThan 1
    }

    @Test
    fun age_iso_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(AgeVerificationScheme, ISO_MDOC)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        coseCompliantSerializer.decodeFromByteArray<IssuerSigned>(serializedCredential.decodeToByteArray(Base64()))
            .namespaces?.values?.firstOrNull()
            ?.entries?.size.shouldNotBeNull() shouldBeGreaterThan 1
    }

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
})

