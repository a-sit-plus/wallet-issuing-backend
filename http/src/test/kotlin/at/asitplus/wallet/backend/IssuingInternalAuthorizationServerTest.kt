package at.asitplus.wallet.backend


import at.asitplus.openid.CredentialResponseParameters
import at.asitplus.signum.indispensable.josef.JwsSigned
import at.asitplus.wallet.companyregistration.CompanyRegistrationScheme
import at.asitplus.wallet.cor.CertificateOfResidenceScheme
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.healthid.HealthIdScheme
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.*
import at.asitplus.wallet.lib.data.VerifiableCredentialJws
import at.asitplus.wallet.lib.data.VerifiableCredentialSdJwt
import at.asitplus.wallet.lib.iso.IssuerSigned
import at.asitplus.wallet.lib.oauth2.OAuth2Client
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.WalletService
import at.asitplus.wallet.lib.openid.AuthenticationResponseResult
import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
import at.asitplus.wallet.por.PowerOfRepresentationScheme
import at.asitplus.wallet.taxid.TaxIdScheme
import com.benasher44.uuid.uuid4
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
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

/**
 * Tests the issuing process,
 * by facilitating the external authorization server.
 */
@SpringBootTest
class IssuingInternalAuthorizationServerTest {

    @Autowired
    private lateinit var credentialIssuer: CredentialIssuer

    @Autowired
    private lateinit var authorizationServer: SimpleAuthorizationService

    @Test
    @WithOAuth2AuthenticationToken
    fun ida_vc_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(IdAustriaScheme, PLAIN_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credential.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential).getOrThrow()
        val vcJws = VerifiableCredentialJws.deserialize(jws.payload.decodeToString()).getOrThrow().shouldNotBeNull()
        val subject = vcJws.vc.credentialSubject.shouldBeInstanceOf<IdAustriaCredential>()
        subject.dateOfBirth shouldBe LocalDate(1983, 6, 4)
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun pid_vc_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(EuPidScheme, PLAIN_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credential.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential).getOrThrow()
        val vcJws = VerifiableCredentialJws.deserialize(jws.payload.decodeToString()).getOrThrow().shouldNotBeNull()
        val subject = vcJws.vc.credentialSubject.shouldBeInstanceOf<EuPidCredential>()
        subject.birthDate shouldBe LocalDate(1983, 6, 4)
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun ida_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(IdAustriaScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credential.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        val vcJws = VerifiableCredentialSdJwt.deserialize(jws.payload.decodeToString()).getOrThrow().shouldNotBeNull()
        vcJws.disclosureDigests!!.size shouldBeGreaterThan 1
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun pid_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(EuPidScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credential.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        val vcJws = VerifiableCredentialSdJwt.deserialize(jws.payload.decodeToString()).getOrThrow().shouldNotBeNull()
        vcJws.disclosureDigests!!.size shouldBeGreaterThan 1
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun pid_iso_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(EuPidScheme, ISO_MDOC)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credential.shouldNotBeNull()
        val issuerSigned = IssuerSigned.deserialize(serializedCredential.decodeToByteArray(Base64())).getOrThrow()
        val numberOfClaims = issuerSigned.namespaces?.values?.firstOrNull()?.entries?.size.shouldNotBeNull()
        numberOfClaims shouldBeGreaterThan 1
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun cor_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(CertificateOfResidenceScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credential.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        val vcJws = VerifiableCredentialSdJwt.deserialize(jws.payload.decodeToString()).getOrThrow().shouldNotBeNull()
        vcJws.disclosureDigests!!.size shouldBeGreaterThan 1
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun por_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(PowerOfRepresentationScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credential.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        val vcJws = VerifiableCredentialSdJwt.deserialize(jws.payload.decodeToString()).getOrThrow().shouldNotBeNull()
        vcJws.disclosureDigests!!.size shouldBeGreaterThan 1
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun cr_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(CompanyRegistrationScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credential.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        val vcJws = VerifiableCredentialSdJwt.deserialize(jws.payload.decodeToString()).getOrThrow().shouldNotBeNull()
        vcJws.disclosureDigests!!.size shouldBeGreaterThan 1
    }

    @Disabled("Need to enter correct URL and api-key")
    @Test
    @WithOAuth2AuthenticationToken
    fun healthid_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(HealthIdScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credential.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        val vcJws = VerifiableCredentialSdJwt.deserialize(jws.payload.decodeToString()).getOrThrow().shouldNotBeNull()
        vcJws.disclosureDigests!!.size shouldBeGreaterThan 1
    }


    @Test
    @WithOAuth2AuthenticationToken
    fun taxid_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(TaxIdScheme, SD_JWT)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credential.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        val vcJws = VerifiableCredentialSdJwt.deserialize(jws.payload.decodeToString()).getOrThrow().shouldNotBeNull()
        vcJws.disclosureDigests!!.size shouldBeGreaterThan 1
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun mdl_iso_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(MobileDrivingLicenceScheme, ISO_MDOC)

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credential.shouldNotBeNull()
        val issuerSigned = IssuerSigned.deserialize(serializedCredential.decodeToByteArray(Base64())).getOrThrow()
        val numberOfClaims = issuerSigned.namespaces?.values?.firstOrNull()?.entries?.size.shouldNotBeNull()
        numberOfClaims shouldBeGreaterThan 1
    }

    private suspend fun loadCredential(requestOptions: WalletService.RequestOptions): CredentialResponseParameters {
        val client = Client()
        val state = uuid4().toString()
        val offer = credentialIssuer.credentialOfferWithAuthorizationCode()
        val scope = client.oid4vciClient.buildScope(requestOptions, credentialIssuer.metadata)
        val authnRequest =
            client.oid4vciClient.oauth2Client.createAuthRequest(state, authorizationDetails = null, scope = scope)
        val authorizationCode = authorizationServer.authorize(authnRequest).getOrThrow()
        authorizationCode.shouldBeInstanceOf<AuthenticationResponseResult.Redirect>()
        val tokenRequest = client.oid4vciClient.oauth2Client.createTokenRequestParameters(
            state,
            OAuth2Client.AuthorizationForToken.Code(authorizationCode.params.code!!),
            authorizationDetails = null,
            scope = scope
        )
        val accessToken = authorizationServer.token(tokenRequest).getOrThrow()
        val credentialRequest = client.oid4vciClient.createCredentialRequest(
            input = WalletService.CredentialRequestInput.RequestOptions(requestOptions),
            clientNonce = accessToken.clientNonce,
            credentialIssuer = offer.credentialIssuer
        ).getOrThrow()
        val credential = credentialIssuer.credential(
            accessToken = accessToken.accessToken,
            params = credentialRequest,
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
        val idToken = OidcIdToken(
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
        val authorities = AuthorityUtils.createAuthorityList("notimportant")
        val principal = DefaultOidcUser(authorities, idToken)
        val authentication = OAuth2AuthenticationToken(principal, authorities, "clientId")
        context.authentication = authentication
        return context
    }
}

@Retention(AnnotationRetention.RUNTIME)
@WithSecurityContext(factory = WithOAuth2AuthenticationTokenSecurityContextFactory::class)
annotation class WithOAuth2AuthenticationToken