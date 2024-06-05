package at.asitplus.wallet.backend

import at.asitplus.crypto.datatypes.jws.JwsSigned
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.VerifiableCredentialJws
import at.asitplus.wallet.lib.data.VerifiableCredentialSdJwt
import at.asitplus.wallet.lib.iso.IssuerSigned
import at.asitplus.wallet.lib.oidc.AuthenticationResponseResult
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.CredentialResponseParameters
import at.asitplus.wallet.lib.oidvci.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.WalletService
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @WithOAuth2AuthenticationToken
    fun ida_vc_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(
            credentialScheme = IdAustriaScheme,
            representation = ConstantIndex.CredentialRepresentation.PLAIN_JWT,
        )

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credential.shouldNotBeNull()
        val jws = JwsSigned.parse(serializedCredential).getOrThrow()
        val vcJws = VerifiableCredentialJws.deserialize(jws.payload.decodeToString()).getOrThrow().shouldNotBeNull()
        val subject = vcJws.vc.credentialSubject.shouldBeInstanceOf<IdAustriaCredential>()
        subject.dateOfBirth shouldBe LocalDate(1983, 6, 4)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @WithOAuth2AuthenticationToken
    fun pid_vc_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(
            credentialScheme = EuPidScheme,
            representation = ConstantIndex.CredentialRepresentation.PLAIN_JWT,
        )

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credential.shouldNotBeNull()
        val jws = JwsSigned.parse(serializedCredential).getOrThrow()
        val vcJws = VerifiableCredentialJws.deserialize(jws.payload.decodeToString()).getOrThrow().shouldNotBeNull()
        val subject = vcJws.vc.credentialSubject.shouldBeInstanceOf<EuPidCredential>()
        subject.birthDate shouldBe LocalDate(1983, 6, 4)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @WithOAuth2AuthenticationToken
    fun ida_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(
            credentialScheme = IdAustriaScheme,
            representation = ConstantIndex.CredentialRepresentation.SD_JWT,
        )

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credential.shouldNotBeNull()
        val jws = JwsSigned.parse(serializedCredential.substringBeforeLast("~")).getOrThrow()
        val vcJws = VerifiableCredentialSdJwt.deserialize(jws.payload.decodeToString()).getOrThrow().shouldNotBeNull()
        vcJws.disclosureDigests.size shouldBeGreaterThan 1
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @WithOAuth2AuthenticationToken
    fun pid_sdjwt_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(
            credentialScheme = EuPidScheme,
            representation = ConstantIndex.CredentialRepresentation.SD_JWT,
        )

        val credential = loadCredential(requestOptions)

        val serializedCredential = credential.credential.shouldNotBeNull()
        val jws = JwsSigned.parse(serializedCredential.substringBeforeLast("~")).getOrThrow()
        val vcJws = VerifiableCredentialSdJwt.deserialize(jws.payload.decodeToString()).getOrThrow().shouldNotBeNull()
        vcJws.disclosureDigests.size shouldBeGreaterThan 1
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @WithOAuth2AuthenticationToken
    fun mdl_iso_ok() = runTest {
        val requestOptions = WalletService.RequestOptions(
            credentialScheme = ConstantIndex.MobileDrivingLicence2023,
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
        val offer = credentialIssuer.credentialOffer()
        val authnRequest = client.oid4vciClient.createAuthRequest(requestOptions)
        val authorizationCode = authorizationServer.authorize(authnRequest).getOrThrow()
        authorizationCode.shouldBeInstanceOf<AuthenticationResponseResult.Redirect>()
        val tokenRequest = client.oid4vciClient.createTokenRequestParameters(
            requestOptions = requestOptions,
            code = authorizationCode.params.code!!,
            state = requestOptions.state,
        )
        val accessToken = authorizationServer.token(tokenRequest).getOrThrow()
        val credentialRequest = when (requestOptions.representation) {
            ConstantIndex.CredentialRepresentation.ISO_MDOC -> client.oid4vciClient.createCredentialRequestCwt(
                requestOptions = requestOptions,
                clientNonce = accessToken.clientNonce,
                credentialIssuer = offer.credentialIssuer
            ).getOrThrow()

            else -> client.oid4vciClient.createCredentialRequestJwt(
                requestOptions = requestOptions,
                clientNonce = accessToken.clientNonce,
                credentialIssuer = offer.credentialIssuer
            ).getOrThrow()
        }
        val credential = credentialIssuer.credential(
            accessToken = accessToken.accessToken,
            params = credentialRequest,
        ).getOrThrow()
        return credential
    }

}