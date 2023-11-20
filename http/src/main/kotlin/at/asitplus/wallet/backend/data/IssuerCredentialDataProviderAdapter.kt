package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.idaustria.IdAustriaCredential
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
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.time.Duration


/**
 * Implements interface from VC Library to wrap calls to a specific [CredentialDataProvider].
 */
class IssuerCredentialDataProviderAdapter(
    private val lifetime: Duration,
    private val authenticationSupplier: AuthenticationSupplier,
) : IssuerCredentialDataProvider {

    override fun getCredential(
        subjectPublicKey: CryptoPublicKey,
        credentialScheme: ConstantIndex.CredentialScheme,
        representation: ConstantIndex.CredentialRepresentation
    ): KmmResult<List<CredentialToBeIssued>> {
        val maxExpiration = Clock.System.now() + lifetime
        Napier.v("getCredential for $credentialScheme and $subjectPublicKey in $representation")
        val idToken = authenticationSupplier.getCurrentUserOidcDetails()
        if (credentialScheme == at.asitplus.wallet.idaustria.ConstantIndex.IdAustriaCredential) {
            Napier.v("getCredential user is $idToken")
            if (idToken == null) {
                return KmmResult.success(listOf())
            }
            val portrait = idToken.getClaimAsString("org.iso.18013.5.1:portrait")
                ?.decodeToByteArray(Base64())
            val dateOfBirth = LocalDate.parse(idToken.birthdate)
            val singleItem = when (representation) {
                ConstantIndex.CredentialRepresentation.PLAIN_JWT -> CredentialToBeIssued.VcJwt(
                    subject = IdAustriaCredential(
                        id = subjectPublicKey.toJsonWebKey().identifier,
                        firstname = idToken.givenName,
                        lastname = idToken.familyName,
                        dateOfBirth = dateOfBirth,
                        portrait = portrait
                    ),
                    expiration = maxExpiration,
                )

                ConstantIndex.CredentialRepresentation.SD_JWT -> CredentialToBeIssued.VcSd(
                    claims = listOfNotNull(
                        ClaimToBeIssued("firstname", idToken.givenName),
                        ClaimToBeIssued("lastname", idToken.familyName),
                        ClaimToBeIssued("date-of-birth", idToken.birthdate),
                        portrait?.encodeToString(Base64())?.let { ClaimToBeIssued("date-of-birth", it) },
                    ),
                    expiration = maxExpiration
                )

                ConstantIndex.CredentialRepresentation.ISO_MDOC -> CredentialToBeIssued.Iso(
                    issuerSignedItems = listOfNotNull(
                        item(0U, "firstname", idToken.givenName),
                        item(1U, "lastname", idToken.familyName),
                        item(2U, "date-of-birth", idToken.birthdate),
                        portrait?.let { item(3U, "portrait", it) },
                    ),
                    expiration = maxExpiration
                )
            }
            return KmmResult.success(listOf(singleItem))
        }
        return KmmResult.success(listOf())
    }

    private fun item(digestId: UInt, name: String, stringValue: String) = IssuerSignedItem(
        digestId,
        Random.nextBytes(16),
        name,
        ElementValue(string = stringValue)
    )

    private fun item(digestId: UInt, name: String, dateValue: LocalDate) = IssuerSignedItem(
        digestId,
        Random.nextBytes(16),
        name,
        ElementValue(date = dateValue)
    )

    private fun item(digestId: UInt, name: String, byteValue: ByteArray) = IssuerSignedItem(
        digestId,
        Random.nextBytes(16),
        name,
        ElementValue(bytes = byteValue)
    )

}
