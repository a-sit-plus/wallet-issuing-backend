package at.asitplus.wallet.backend

import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.LocalDate
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
 * i.e. it skips the authentication process entirely by using [WithOAuth2AuthenticationToken].
 */
@SpringBootTest
class IssuingTest {

    @Autowired
    private lateinit var issuerCredentialDataProvider: IssuerCredentialDataProvider

    @Test
    @WithOAuth2AuthenticationToken
    fun ida_vc_ok() {
        val client = Client()
        val credential = issuerCredentialDataProvider.getCredential(
            subjectPublicKey = client.jsonWebKey.toCryptoPublicKey().getOrThrow(),
            credentialScheme = IdAustriaScheme,
            representation = ConstantIndex.CredentialRepresentation.PLAIN_JWT,
        ).getOrThrow()

        credential.shouldBeSingleton()
        val single = credential.single()
        single.shouldBeInstanceOf<CredentialToBeIssued.VcJwt>()
        val subject = single.subject
        subject.shouldBeInstanceOf<IdAustriaCredential>()
        subject.dateOfBirth shouldBe LocalDate(1983, 6, 4)
        subject.bpk shouldBe "ZP-MH:KQMY8Sl9WsmBxrYrYOiFS2VkLyo="
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun pid_vc_ok() {
        val client = Client()
        val credential = issuerCredentialDataProvider.getCredential(
            subjectPublicKey = client.jsonWebKey.toCryptoPublicKey().getOrThrow(),
            credentialScheme = EuPidScheme,
            representation = ConstantIndex.CredentialRepresentation.PLAIN_JWT,
        ).getOrThrow()

        credential.shouldBeSingleton()
        val single = credential.single()
        single.shouldBeInstanceOf<CredentialToBeIssued.VcJwt>()
        val subject = single.subject
        subject.shouldBeInstanceOf<EuPidCredential>()
        subject.birthDate shouldBe LocalDate(1983, 6, 4)
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun ida_sdjwt_ok() {
        val client = Client()
        val credential = issuerCredentialDataProvider.getCredential(
            subjectPublicKey = client.jsonWebKey.toCryptoPublicKey().getOrThrow(),
            credentialScheme = IdAustriaScheme,
            representation = ConstantIndex.CredentialRepresentation.SD_JWT,
        ).getOrThrow()

        credential.shouldBeSingleton()
        val single = credential.single()
        single.shouldBeInstanceOf<CredentialToBeIssued.VcSd>()
        single.claims.shouldNotBeEmpty()
        single.claims
            .first { it.name == IdAustriaScheme.Attributes.DATE_OF_BIRTH }
            .value shouldBe LocalDate(1983, 6, 4)
        single.claims
            .first { it.name == IdAustriaScheme.Attributes.BPK }
            .value shouldBe "ZP-MH:KQMY8Sl9WsmBxrYrYOiFS2VkLyo="
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun pid_sdjwt_ok() {
        val client = Client()
        val credential = issuerCredentialDataProvider.getCredential(
            subjectPublicKey = client.jsonWebKey.toCryptoPublicKey().getOrThrow(),
            credentialScheme = EuPidScheme,
            representation = ConstantIndex.CredentialRepresentation.SD_JWT,
        ).getOrThrow()

        credential.shouldBeSingleton()
        val single = credential.single()
        single.shouldBeInstanceOf<CredentialToBeIssued.VcSd>()
        single.claims.shouldNotBeEmpty()
        single.claims
            .first { it.name == EuPidScheme.Attributes.BIRTH_DATE }
            .value shouldBe LocalDate(1983, 6, 4)
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun ida_iso_ok() {
        val client = Client()
        val credential = issuerCredentialDataProvider.getCredential(
            subjectPublicKey = client.jsonWebKey.toCryptoPublicKey().getOrThrow(),
            credentialScheme = IdAustriaScheme,
            representation = ConstantIndex.CredentialRepresentation.ISO_MDOC,
        ).getOrThrow()

        credential.shouldBeSingleton()
        val single = credential.single()
        single.shouldBeInstanceOf<CredentialToBeIssued.Iso>()
        single.issuerSignedItems.shouldNotBeEmpty()
        single.issuerSignedItems
            .first { it.elementIdentifier == IdAustriaScheme.Attributes.DATE_OF_BIRTH }
            .elementValue shouldBe LocalDate(1983, 6, 4)
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun pid_iso_ok() {
        val client = Client()
        val credential = issuerCredentialDataProvider.getCredential(
            subjectPublicKey = client.jsonWebKey.toCryptoPublicKey().getOrThrow(),
            credentialScheme = EuPidScheme,
            representation = ConstantIndex.CredentialRepresentation.ISO_MDOC,
        ).getOrThrow()

        credential.shouldBeSingleton()
        val single = credential.single()
        single.shouldBeInstanceOf<CredentialToBeIssued.Iso>()
        single.issuerSignedItems.shouldNotBeEmpty()
        single.issuerSignedItems
            .first { it.elementIdentifier == EuPidScheme.Attributes.BIRTH_DATE }
            .elementValue shouldBe LocalDate(1983, 6, 4)
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun mdl_iso_ok() {
        val client = Client()
        val credential = issuerCredentialDataProvider.getCredential(
            subjectPublicKey = client.jsonWebKey.toCryptoPublicKey().getOrThrow(),
            credentialScheme = MobileDrivingLicenceScheme,
            representation = ConstantIndex.CredentialRepresentation.ISO_MDOC,
        ).getOrThrow()

        credential.shouldBeSingleton()
        val single = credential.single()
        single.shouldBeInstanceOf<CredentialToBeIssued.Iso>()
        single.issuerSignedItems.shouldNotBeEmpty()
        single.issuerSignedItems
            .first { it.elementIdentifier == MobileDrivingLicenceDataElements.BIRTH_DATE }
            .elementValue shouldBe LocalDate(1983, 6, 4)
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