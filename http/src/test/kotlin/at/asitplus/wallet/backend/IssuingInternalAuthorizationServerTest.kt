package at.asitplus.wallet.backend

import at.asitplus.openid.CredentialResponseParameters
import at.asitplus.signum.indispensable.josef.JwsSigned
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.VerifiableCredentialJws
import at.asitplus.wallet.lib.data.VerifiableCredentialSdJwt
import at.asitplus.wallet.lib.iso.IssuerSigned
import at.asitplus.wallet.lib.oauth2.OAuth2Client
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidc.AuthenticationResponseResult
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.WalletService
import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
import com.benasher44.uuid.uuid4
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

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
        val requestOptions = WalletService.RequestOptions(
            credentialScheme = IdAustriaScheme,
            representation = ConstantIndex.CredentialRepresentation.PLAIN_JWT,
        )

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
        val requestOptions = WalletService.RequestOptions(
            credentialScheme = EuPidScheme,
            representation = ConstantIndex.CredentialRepresentation.PLAIN_JWT,
        )

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
        val requestOptions = WalletService.RequestOptions(
            credentialScheme = IdAustriaScheme,
            representation = ConstantIndex.CredentialRepresentation.SD_JWT,
        )

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credential.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        val vcJws = VerifiableCredentialSdJwt.deserialize(jws.payload.decodeToString()).getOrThrow().shouldNotBeNull()
        vcJws.disclosureDigests!!.size shouldBeGreaterThan 1
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun pid_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(
            credentialScheme = EuPidScheme,
            representation = ConstantIndex.CredentialRepresentation.SD_JWT,
        )

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credential.shouldNotBeNull()
        val jws = JwsSigned.deserialize(serializedCredential.substringBefore("~")).getOrThrow()
        val vcJws = VerifiableCredentialSdJwt.deserialize(jws.payload.decodeToString()).getOrThrow().shouldNotBeNull()
        vcJws.disclosureDigests!!.size shouldBeGreaterThan 1
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun mdl_iso_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(
            credentialScheme = MobileDrivingLicenceScheme,
            representation = ConstantIndex.CredentialRepresentation.ISO_MDOC,
        )

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
        val authorizationDetails = client.oid4vciClient.buildAuthorizationDetails(requestOptions)
        val authnRequest = client.oid4vciClient.oauth2Client.createAuthRequest(state, authorizationDetails)
        val authorizationCode = authorizationServer.authorize(authnRequest).getOrThrow()
        authorizationCode.shouldBeInstanceOf<AuthenticationResponseResult.Redirect>()
        val tokenRequest = client.oid4vciClient.oauth2Client.createTokenRequestParameters(
            state,
            OAuth2Client.AuthorizationForToken.Code(authorizationCode.params.code!!),
            authorizationDetails,
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