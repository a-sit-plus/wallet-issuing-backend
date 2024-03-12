package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.crypto.datatypes.CryptoPublicKey
import at.asitplus.crypto.datatypes.jws.toJsonWebKey
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.idaustria.IdAustriaScheme.Attributes
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
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import kotlin.io.encoding.ExperimentalEncodingApi
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
        Napier.v("getCredential user is $idToken")
        if (idToken == null) {
            Napier.w("getCredential returns null, no IdToken in session")
            return KmmResult.success(listOf())
        }
        val singleItem = when (representation) {
            ConstantIndex.CredentialRepresentation.PLAIN_JWT -> when (credentialScheme) {
                IdAustriaScheme -> idaVcJwt(subjectPublicKey, idToken, maxExpiration)
                EuPidScheme -> eupidVcJwt(subjectPublicKey, idToken, maxExpiration)
                else -> null
            }

            ConstantIndex.CredentialRepresentation.SD_JWT -> when (credentialScheme) {
                IdAustriaScheme -> idaVcSd(claimNames, idToken, maxExpiration)
                EuPidScheme -> eupidVcSd(claimNames, idToken, maxExpiration)
                else -> null
            }


            ConstantIndex.CredentialRepresentation.ISO_MDOC -> when (credentialScheme) {
                IdAustriaScheme -> idaIso(claimNames, idToken, maxExpiration)
                EuPidScheme -> eupidIso(claimNames, idToken, maxExpiration)
                else -> null
            }
        }
        return singleItem?.let {
            KmmResult.success(listOf(it))
        } ?: KmmResult.success(listOf())
    }

    private fun idaIso(
        claimNames: Collection<String>?,
        idToken: OidcIdToken,
        maxExpiration: Instant
    ) = CredentialToBeIssued.Iso(
        issuerSignedItems = buildIdaClaims(claimNames, idToken).mapIndexed(::buildIssuerSignedItem),
        expiration = maxExpiration
    )

    private fun eupidIso(
        claimNames: Collection<String>?,
        idToken: OidcIdToken,
        maxExpiration: Instant
    ) = CredentialToBeIssued.Iso(
        issuerSignedItems = buildEupidClaims(claimNames, idToken).mapIndexed(::buildIssuerSignedItem),
        expiration = maxExpiration
    )

    private fun buildIssuerSignedItem(
        index: Int,
        claimToBeIssued: ClaimToBeIssued
    ) = IssuerSignedItem(
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

    private fun idaVcSd(
        claimNames: Collection<String>?,
        idToken: OidcIdToken,
        maxExpiration: Instant
    ) = CredentialToBeIssued.VcSd(
        claims = buildIdaClaims(claimNames, idToken),
        expiration = maxExpiration
    )

    private fun eupidVcSd(
        claimNames: Collection<String>?,
        idToken: OidcIdToken,
        maxExpiration: Instant
    ) = CredentialToBeIssued.VcSd(
        claims = buildEupidClaims(claimNames, idToken),
        expiration = maxExpiration
    )

    private fun idaVcJwt(
        subjectPublicKey: CryptoPublicKey,
        idToken: OidcIdToken,
        maxExpiration: Instant
    ) = CredentialToBeIssued.VcJwt(
        subject = IdAustriaCredential(
            id = subjectPublicKey.toJsonWebKey().identifier,
            bpk = idToken.bpk,
            firstname = idToken.givenName,
            lastname = idToken.familyName,
            dateOfBirth = idToken.dateOfBirth,
            portrait = idToken.portrait,
            mainAddress = idToken.mainAddress,
            ageOver14 = idToken.ageOver14,
            ageOver16 = idToken.ageOver16,
            ageOver18 = idToken.ageOver18,
            ageOver21 = idToken.ageOver21,
        ),
        expiration = maxExpiration,
    )

    private fun eupidVcJwt(
        subjectPublicKey: CryptoPublicKey,
        idToken: OidcIdToken,
        maxExpiration: Instant
    ) = CredentialToBeIssued.VcJwt(
        subject = EuPidCredential(
            id = subjectPublicKey.toJsonWebKey().identifier,
            familyName = idToken.familyName,
            givenName = idToken.givenName,
            birthDate = idToken.dateOfBirth,
            ageOver18 = idToken.ageOver18,
        ),
        expiration = maxExpiration,
    )

    private fun buildIdaClaims(claimNames: Collection<String>?, idToken: OidcIdToken) = listOfNotNull(
        claim(claimNames, Attributes.BPK, idToken.bpk),
        claim(claimNames, Attributes.FIRSTNAME, idToken.givenName),
        claim(claimNames, Attributes.LASTNAME, idToken.familyName),
        claim(claimNames, Attributes.DATE_OF_BIRTH, idToken.dateOfBirth),
        claim(claimNames, Attributes.PORTRAIT, idToken.portrait),
        claim(claimNames, Attributes.MAIN_ADDRESS, idToken.mainAddress),
        claim(claimNames, Attributes.AGE_OVER_14, idToken.ageOver14),
        claim(claimNames, Attributes.AGE_OVER_16, idToken.ageOver16),
        claim(claimNames, Attributes.AGE_OVER_18, idToken.ageOver18),
        claim(claimNames, Attributes.AGE_OVER_21, idToken.ageOver21),
    )

    private fun buildEupidClaims(claimNames: Collection<String>?, idToken: OidcIdToken) = listOfNotNull(
        claim(claimNames, EuPidScheme.Attributes.FAMILY_NAME, idToken.familyName),
        claim(claimNames, EuPidScheme.Attributes.GIVEN_NAME, idToken.givenName),
        claim(claimNames, EuPidScheme.Attributes.BIRTH_DATE, idToken.dateOfBirth),
        claim(claimNames, EuPidScheme.Attributes.AGE_OVER_18, idToken.ageOver18),
    )

    private fun claim(claimNames: Collection<String>?, key: String, value: Any?) =
        if (claimNames.isNullOrContains(key) && value != null) ClaimToBeIssued(key, value.encodeIfNeeded()) else null

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

@OptIn(ExperimentalEncodingApi::class)
private fun Any.encodeIfNeeded() = if (this is ByteArray) kotlin.io.encoding.Base64.encode(this) else this

