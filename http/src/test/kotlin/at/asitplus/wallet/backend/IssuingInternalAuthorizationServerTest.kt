package at.asitplus.wallet.backend


import at.asitplus.iso.IssuerSignedList
import at.asitplus.openid.CredentialResponseParameters
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.openid.TokenResponseParameters
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.signum.indispensable.josef.JwsSigned
import at.asitplus.wallet.backend.config.NoopEPrescriptionLoader
import at.asitplus.wallet.backend.data.OidcIssuerCredentialDataProvider
import at.asitplus.wallet.companyregistration.CompanyRegistrationDataElements
import at.asitplus.wallet.companyregistration.CompanyRegistrationScheme
import at.asitplus.wallet.cor.CertificateOfResidenceScheme
import at.asitplus.wallet.ehic.EhicScheme
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtScheme
import at.asitplus.wallet.healthid.HealthIdScheme
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.agent.SdJwtValidator
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.*
import at.asitplus.wallet.lib.data.VerifiableCredentialJws
import at.asitplus.wallet.lib.data.VerifiableCredentialSdJwt
import at.asitplus.wallet.lib.data.vckJsonSerializer
import at.asitplus.wallet.lib.iso.IssuerSigned
import at.asitplus.wallet.lib.jws.SdJwtSigned
import at.asitplus.wallet.lib.oauth2.OAuth2Client
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.WalletService
import at.asitplus.wallet.lib.openid.AuthenticationResponseResult
import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
import at.asitplus.wallet.por.PowerOfRepresentationDataElements
import at.asitplus.wallet.por.PowerOfRepresentationScheme
import at.asitplus.wallet.taxid.TaxId2025Scheme
import com.benasher44.uuid.uuid4
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Contextual
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Disabled
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
    private lateinit var issuer: Issuer

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
    fun pid_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(EuPidScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        vckJsonSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString())
            .disclosureDigests!!.size shouldBeGreaterThan 1
    }

    @Test
    fun pid_new_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(EuPidSdJwtScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        vckJsonSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString())
            .disclosureDigests!!.size shouldBeGreaterThan 1
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
            .disclosureDigests!!.size shouldBeGreaterThan 1
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
        SdJwtValidator(SdJwtSigned.parse(serializedCredential)!!).reconstructedJsonObject.shouldNotBeNull()
            .keys.shouldContain(PowerOfRepresentationDataElements.ISSUING_AUTHORITY)
    }

    @Test
    fun cr_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(CompanyRegistrationScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        vckJsonSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString()).apply {
            subject.shouldNotBeNull()
            disclosureDigests.shouldBeNull()
        }

        SdJwtValidator(SdJwtSigned.parse(serializedCredential)!!).reconstructedJsonObject.shouldNotBeNull()
            .keys.shouldContain(CompanyRegistrationDataElements.COMPANY_NAME)
    }

    @Disabled("Need to enter correct URL and api-key")
    @Test
    fun healthid_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(HealthIdScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        vckJsonSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString()).apply {
            subject.shouldNotBeNull()
            disclosureDigests.shouldBeNull()
        }
        SdJwtValidator(SdJwtSigned.parse(serializedCredential)!!).reconstructedJsonObject.shouldNotBeNull()
            .keys.shouldContain(HealthIdScheme.Attributes.ISSUING_AUTHORITY)
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
        SdJwtValidator(
            SdJwtSigned.parse(serializedCredential).shouldNotBeNull()
        ).reconstructedJsonObject.shouldNotBeNull()
    }

    @Suppress("DEPRECATION")
    @Test
    fun taxid_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(at.asitplus.wallet.taxid.TaxIdScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        vckJsonSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString()).apply {
            issuer.shouldNotBeNull()
            disclosureDigests.shouldBeNull()
        }
        SdJwtValidator(
            SdJwtSigned.parse(serializedCredential).shouldNotBeNull()
        ).reconstructedJsonObject.shouldNotBeNull()
    }

    @Test
    fun taxid2025_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(TaxId2025Scheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        vckJsonSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString()).apply {
            issuer.shouldNotBeNull()
            disclosureDigests.shouldBeNull()
        }
        SdJwtValidator(
            SdJwtSigned.parse(serializedCredential).shouldNotBeNull()
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

    private suspend fun loadCredential(requestOptions: WalletService.RequestOptions): CredentialResponseParameters {
        val client = Client()
        val state = uuid4().toString()
        val credentialFormat =
            client.oid4vciClient.selectSupportedCredentialFormat(requestOptions, credentialIssuer.metadata)
                .shouldNotBeNull()
        val scope = credentialFormat.scope
        val authnRequest =
            client.oid4vciClient.oauth2Client.createAuthRequest(state, authorizationDetails = null, scope = scope)
        val authorizationCode = authorizationServer.authorize(authnRequest) { mockOidcUserInfoExtended() }.getOrThrow()
        authorizationCode.shouldBeInstanceOf<AuthenticationResponseResult.Redirect>()
        val tokenRequest = client.oid4vciClient.oauth2Client.createTokenRequestParameters(
            OAuth2Client.AuthorizationForToken.Code(authorizationCode.params.code!!),
            state = state,
            authorizationDetails = null,
            scope = scope
        )
        val accessToken: TokenResponseParameters = authorizationServer.token(tokenRequest).getOrThrow()
        val credentialRequest = client.oid4vciClient.createCredentialRequest(
            tokenResponse = accessToken,
            metadata = credentialIssuer.metadata,
            credentialFormat = credentialFormat,
            clientNonce = credentialIssuer.nonce().getOrThrow().clientNonce
        ).getOrThrow()
        val credential = credentialIssuer.credential(
            authorizationHeader = accessToken.toHttpHeaderValue(),
            params = credentialRequest.first(),
            credentialDataProvider = OidcIssuerCredentialDataProvider(
                lifetime = 1.minutes,
                ePrescriptionLoader = NoopEPrescriptionLoader
            ),
            issueCredential = {
                issuer.issueCredential(it)
            },
        ).getOrThrow()
        return credential
    }

}

private fun mockOidcUserInfoExtended() = OidcUserInfoExtended.deserialize(buildJsonObject {
    put(IdTokenClaimNames.SUB, JsonPrimitive("IFOQP3T5XYLMSDOQAEGMF52MWGMWBPXN"))
    put("birthdate", JsonPrimitive("1983-06-04"))
    put("given_name", JsonPrimitive("XXXŐzgür"))
    put("family_name", JsonPrimitive("XXXTüzekçi"))
    put("urn:pvpgvat:oidc.bpk", JsonPrimitive("ZP-MH:KQMY8Sl9WsmBxrYrYOiFS2VkLyo="))
}.toString())

