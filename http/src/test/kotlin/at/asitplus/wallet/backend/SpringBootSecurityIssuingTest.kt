package at.asitplus.wallet.backend

import at.asitplus.catching
import at.asitplus.openid.CredentialResponseParameters
import at.asitplus.openid.TokenResponseParameters
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.signum.indispensable.josef.JwsSigned
import at.asitplus.wallet.backend.auth.SpringSecurityAuthenticationSupplier
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
import at.asitplus.wallet.lib.oidvci.WalletService.RequestOptions
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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import org.junit.jupiter.api.Disabled
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
    private lateinit var issuer: Issuer

    @Autowired
    private lateinit var credentialIssuer: CredentialIssuer

    @Autowired
    private lateinit var authorizationServer: SimpleAuthorizationService

    @Test
    @WithOAuth2AuthenticationToken
    fun pid_vc_ok() = runTest {
        val requestOptions = RequestOptions(EuPidScheme, PLAIN_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential).getOrThrow()
        vckJsonSerializer.decodeFromString<VerifiableCredentialJws>(jws.payload.decodeToString())
            .vc.credentialSubject.shouldBeInstanceOf<EuPidCredential>()
            .birthDate shouldBe LocalDate(1983, 6, 4)
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun pid_sdjwt_ok() = runTest {
        val requestOptions = RequestOptions(EuPidScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        vckJsonSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString())
            .disclosureDigests!!.size shouldBeGreaterThan 1
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun pid_new_sdjwt_ok() = runTest {
        val requestOptions =
            RequestOptions(EuPidSdJwtScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        vckJsonSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString())
            .disclosureDigests!!.size shouldBeGreaterThan 1
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun pid_iso_ok() = runTest {
        val requestOptions = RequestOptions(EuPidScheme, ISO_MDOC)

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
        val requestOptions =
            RequestOptions(CertificateOfResidenceScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        vckJsonSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString())
            .disclosureDigests!!.size shouldBeGreaterThan 1
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun por_sdjwt_ok() = runTest {
        val requestOptions =
            RequestOptions(PowerOfRepresentationScheme, SD_JWT)

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
    @WithOAuth2AuthenticationToken
    fun cr_sdjwt_ok() = runTest {
        val requestOptions =
            RequestOptions(CompanyRegistrationScheme, SD_JWT)

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
    @WithOAuth2AuthenticationToken
    fun healthid_sdjwt_ok() = runTest {
        val requestOptions = RequestOptions(HealthIdScheme, SD_JWT)

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
    @WithOAuth2AuthenticationToken
    fun ehic_ok() = runTest {
        val requestOptions = RequestOptions(EhicScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        vckJsonSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString()).apply {
            subject.shouldNotBeNull()
            disclosureDigests.shouldBeNull()
        }
        SdJwtValidator(
            SdJwtSigned.parse(serializedCredential).shouldNotBeNull()
        ).reconstructedJsonObject.shouldNotBeNull()
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun taxid2025_ok() = runTest {
        val requestOptions =
            RequestOptions(TaxId2025Scheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        vckJsonSerializer.decodeFromString<VerifiableCredentialSdJwt>(jws.payload.decodeToString()).apply {
            subject.shouldNotBeNull()
            disclosureDigests.shouldBeNull()
        }
        SdJwtValidator(
            SdJwtSigned.parse(serializedCredential).shouldNotBeNull()
        ).reconstructedJsonObject.shouldNotBeNull()
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun mdl_iso_ok() = runTest {
        val requestOptions =
            RequestOptions(MobileDrivingLicenceScheme, ISO_MDOC)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()
        coseCompliantSerializer.decodeFromByteArray<IssuerSigned>(serializedCredential.decodeToByteArray(Base64()))
            .namespaces?.values?.firstOrNull()
            ?.entries?.size.shouldNotBeNull() shouldBeGreaterThan 1
    }

    private suspend fun loadCredential(requestOptions: RequestOptions): CredentialResponseParameters {
        val client = Client()
        val state = uuid4().toString()
        val credentialFormat =
            client.oid4vciClient.selectSupportedCredentialFormat(requestOptions, credentialIssuer.metadata)
                .shouldNotBeNull()
        val scope = credentialFormat.scope
        val authnRequest =
            client.oid4vciClient.oauth2Client.createAuthRequest(state, authorizationDetails = null, scope = scope)
        val authorizationCode = authorizationServer.authorize(authnRequest) {
            catching {
                SpringSecurityAuthenticationSupplier.toOidcUserInfoExtended(SecurityContextHolder.getContext()?.authentication)
                    ?: throw IllegalArgumentException("No authenticated user")
            }
        }.getOrThrow()
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
        ).getOrThrow()
        return credential
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
