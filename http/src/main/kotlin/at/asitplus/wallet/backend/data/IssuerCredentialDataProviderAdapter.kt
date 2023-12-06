package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.idaustria.IdAustriaScheme.Attributes
import at.asitplus.wallet.lib.CryptoPublicKey
import at.asitplus.wallet.lib.agent.ClaimToBeIssued
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.iso.ElementValue
import at.asitplus.wallet.lib.iso.IssuerSignedItem
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import kotlin.random.Random
import kotlin.time.Duration


/**
 * Implements interface from VC Library to extract data from [OidcIdToken] and issue the credentials.
 */
class IssuerCredentialDataProviderAdapter(
    private val lifetime: Duration,
    private val authenticationSupplier: AuthenticationSupplier,
) : IssuerCredentialDataProvider {

    override fun getCredential(
        subjectPublicKey: CryptoPublicKey,
        credentialScheme: ConstantIndex.CredentialScheme,
        representation: ConstantIndex.CredentialRepresentation,
        claimNames: Collection<String>?
    ): KmmResult<List<CredentialToBeIssued>> {
        val maxExpiration = Clock.System.now() + lifetime
        Napier.v("getCredential for $credentialScheme and $subjectPublicKey in $representation")
        val idToken = authenticationSupplier.getCurrentUserOidcDetails()
        if (credentialScheme != IdAustriaScheme) {
            return KmmResult.success(listOf())
        }
        Napier.v("getCredential user is $idToken")
        if (idToken == null) {
            return KmmResult.success(listOf())
        }
        val singleItem = when (representation) {
            ConstantIndex.CredentialRepresentation.PLAIN_JWT -> CredentialToBeIssued.VcJwt(
                subject = IdAustriaCredential(
                    id = subjectPublicKey.toJsonWebKey().identifier,
                    bpk = idToken.bpk,
                    firstname = idToken.givenName,
                    lastname = idToken.familyName,
                    dateOfBirth = idToken.dateOfBirth,
                    portrait = idToken.portrait,
                    ageOver14 = idToken.ageOver14,
                    ageOver16 = idToken.ageOver16,
                    ageOver18 = idToken.ageOver18,
                    ageOver21 = idToken.ageOver21,
                ),
                expiration = maxExpiration,
            )

            ConstantIndex.CredentialRepresentation.SD_JWT -> CredentialToBeIssued.VcSd(
                claims = buildClaims(claimNames, idToken),
                expiration = maxExpiration
            )

            ConstantIndex.CredentialRepresentation.ISO_MDOC -> CredentialToBeIssued.Iso(
                issuerSignedItems = buildClaims(claimNames, idToken).mapIndexed { index, claimToBeIssued ->
                    IssuerSignedItem(
                        digestId = index.toUInt(),
                        random = Random.nextBytes(16),
                        elementIdentifier = claimToBeIssued.name,
                        elementValue = when (val value = claimToBeIssued.value) {
                            is String -> ElementValue(string = value)
                            is ByteArray -> ElementValue(bytes = value)
                            is LocalDate -> ElementValue(date = value)
                            is Boolean -> ElementValue(boolean = value)
                            else -> ElementValue(string = value.toString())
                        }
                    )
                },
                expiration = maxExpiration
            )
        }

        return KmmResult.success(listOf(singleItem))
    }

    private fun buildClaims(claimNames: Collection<String>?, idToken: OidcIdToken) = listOfNotNull(
        claim(claimNames, Attributes.BPK, idToken.bpk),
        claim(claimNames, Attributes.FIRSTNAME, idToken.givenName),
        claim(claimNames, Attributes.LASTNAME, idToken.familyName),
        claim(claimNames, Attributes.DATE_OF_BIRTH, idToken.birthdate),
        claim(claimNames, Attributes.PORTRAIT, idToken.portrait),
        claim(claimNames, Attributes.MAIN_ADDRESS, idToken.mainAddress),
        claim(claimNames, Attributes.AGE_OVER_14, idToken.ageOver14),
        claim(claimNames, Attributes.AGE_OVER_16, idToken.ageOver16),
        claim(claimNames, Attributes.AGE_OVER_18, idToken.ageOver18),
        claim(claimNames, Attributes.AGE_OVER_21, idToken.ageOver21),
    )

    private fun claim(claimNames: Collection<String>?, key: String, value: Any?) =
        if (claimNames.isNullOrContains(key) && value != null) ClaimToBeIssued(key, value) else null

    private fun Collection<String>?.isNullOrContains(name: String) =
        this == null || contains(name)

    private val OidcIdToken.bpk: String
        get() = getClaimAsString("urn:pvpgvat:oidc.bpk") ?: subject

    private val OidcIdToken.ageOver14: Boolean?
        get() = getClaimAsBoolean("org.iso.18013.5.1:age_over_14")

    private val OidcIdToken.ageOver16: Boolean?
        get() = getClaimAsBoolean("org.iso.18013.5.1:age_over_16")

    private val OidcIdToken.ageOver18: Boolean?
        get() = getClaimAsBoolean("org.iso.18013.5.1:age_over_18")

    private val OidcIdToken.ageOver21: Boolean?
        get() = getClaimAsBoolean("org.iso.18013.5.1:age_over_21")

    private val OidcIdToken.dateOfBirth
        get() = LocalDate.parse(birthdate)

    private val OidcIdToken.portrait: ByteArray?
        get() = getClaimAsString("org.iso.18013.5.1:portrait")?.decodeToByteArray(Base64())

    private val OidcIdToken.mainAddress: String?
        get() = getClaimAsString("urn:eidgvat:attributes.mainAddress")

}
