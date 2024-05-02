package at.asitplus.wallet.backend.config

import at.asitplus.KmmResult
import at.asitplus.crypto.datatypes.CryptoPublicKey
import at.asitplus.crypto.datatypes.jws.toJsonWebKey
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
import at.asitplus.wallet.lib.iso.MobileDrivingLicenceDataElements
import at.asitplus.wallet.lib.oidvci.OidcUserInfoExtended
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.time.Duration

class OidcIssuerCredentialDataProvider(
    private val userInfo: OidcUserInfoExtended,
    private val lifetime: Duration
) : IssuerCredentialDataProvider {

    override fun getCredential(
        subjectPublicKey: CryptoPublicKey,
        credentialScheme: ConstantIndex.CredentialScheme,
        representation: ConstantIndex.CredentialRepresentation,
        claimNames: Collection<String>?
    ): KmmResult<List<CredentialToBeIssued>> {
        val maxExpiration = Clock.System.now() + lifetime
        Napier.v("getCredential for $credentialScheme and $subjectPublicKey in $representation")
        Napier.v("getCredential user is $userInfo")
        val singleItem = when (representation) {
            ConstantIndex.CredentialRepresentation.PLAIN_JWT -> when (credentialScheme) {
                IdAustriaScheme -> idaVcJwt(subjectPublicKey, userInfo, maxExpiration)
                EuPidScheme -> eupidVcJwt(subjectPublicKey, userInfo, maxExpiration)
                else -> null
            }

            ConstantIndex.CredentialRepresentation.SD_JWT -> when (credentialScheme) {
                IdAustriaScheme -> idaVcSd(claimNames, userInfo, maxExpiration)
                EuPidScheme -> eupidVcSd(claimNames, userInfo, maxExpiration)
                else -> null
            }


            ConstantIndex.CredentialRepresentation.ISO_MDOC -> when (credentialScheme) {
                IdAustriaScheme -> idaIso(claimNames, userInfo, maxExpiration)
                EuPidScheme -> eupidIso(claimNames, userInfo, maxExpiration)
                ConstantIndex.MobileDrivingLicence2023 -> mdlIso(claimNames, userInfo, maxExpiration)
                else -> null
            }
        }
        return singleItem?.let {
            KmmResult.success(listOf(it))
        } ?: KmmResult.success(listOf())
    }

    private fun idaIso(
        claimNames: Collection<String>?,
        idToken: OidcUserInfoExtended,
        maxExpiration: Instant
    ) = CredentialToBeIssued.Iso(
        issuerSignedItems = buildIdaClaims(claimNames, idToken).mapIndexed(::buildIssuerSignedItem),
        expiration = maxExpiration
    )

    private fun eupidIso(
        claimNames: Collection<String>?,
        idToken: OidcUserInfoExtended,
        maxExpiration: Instant
    ) = CredentialToBeIssued.Iso(
        issuerSignedItems = buildEupidClaims(claimNames, idToken).mapIndexed(::buildIssuerSignedItem),
        expiration = maxExpiration
    )

    private fun mdlIso(
        claimNames: Collection<String>?,
        idToken: OidcUserInfoExtended,
        maxExpiration: Instant
    ) = CredentialToBeIssued.Iso(
        issuerSignedItems = buildMdlClaims(claimNames, idToken).mapIndexed(::buildIssuerSignedItem),
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
        idToken: OidcUserInfoExtended,
        maxExpiration: Instant
    ) = CredentialToBeIssued.VcSd(
        claims = buildIdaClaims(claimNames, idToken),
        expiration = maxExpiration
    )

    private fun eupidVcSd(
        claimNames: Collection<String>?,
        idToken: OidcUserInfoExtended,
        maxExpiration: Instant
    ) = CredentialToBeIssued.VcSd(
        claims = buildEupidClaims(claimNames, idToken),
        expiration = maxExpiration
    )

    private fun idaVcJwt(
        subjectPublicKey: CryptoPublicKey,
        idToken: OidcUserInfoExtended,
        maxExpiration: Instant
    ) = CredentialToBeIssued.VcJwt(
        subject = IdAustriaCredential(
            id = subjectPublicKey.toJsonWebKey().identifier,
            bpk = idToken.bpk,
            firstname = idToken.userInfo.givenName ?: "N/A",
            lastname = idToken.userInfo.familyName ?: "N/A",
            dateOfBirth = idToken.dateOfBirth ?: LocalDate.fromEpochDays(0),
            portrait = idToken.portrait,
            mainAddress = idToken.mainAddress,
            ageOver18 = idToken.userInfo.ageOver18,
        ),
        expiration = maxExpiration,
    )

    private fun eupidVcJwt(
        subjectPublicKey: CryptoPublicKey,
        idToken: OidcUserInfoExtended,
        maxExpiration: Instant
    ) = CredentialToBeIssued.VcJwt(
        subject = EuPidCredential(
            id = subjectPublicKey.toJsonWebKey().identifier,
            familyName = idToken.userInfo.familyName ?: "N/A",
            givenName = idToken.userInfo.givenName ?: "N/A",
            birthDate = idToken.dateOfBirth ?: LocalDate.fromEpochDays(0),
            ageOver18 = idToken.userInfo.ageOver18,
        ),
        expiration = maxExpiration,
    )

    private fun buildIdaClaims(claimNames: Collection<String>?, idToken: OidcUserInfoExtended) = listOfNotNull(
        claim(claimNames, Attributes.BPK, idToken.bpk),
        claim(claimNames, Attributes.FIRSTNAME, idToken.userInfo.givenName),
        claim(claimNames, Attributes.LASTNAME, idToken.userInfo.familyName),
        claim(claimNames, Attributes.DATE_OF_BIRTH, idToken.dateOfBirth),
        claim(claimNames, Attributes.PORTRAIT, idToken.portrait),
        claim(claimNames, Attributes.MAIN_ADDRESS, idToken.mainAddress),
        claim(claimNames, Attributes.AGE_OVER_18, idToken.userInfo.ageOver18),
    )

    private fun buildEupidClaims(claimNames: Collection<String>?, idToken: OidcUserInfoExtended) = listOfNotNull(
        claim(claimNames, EuPidScheme.Attributes.FAMILY_NAME, idToken.userInfo.familyName),
        claim(claimNames, EuPidScheme.Attributes.GIVEN_NAME, idToken.userInfo.givenName),
        claim(claimNames, EuPidScheme.Attributes.BIRTH_DATE, idToken.dateOfBirth),
        claim(claimNames, EuPidScheme.Attributes.AGE_OVER_18, idToken.userInfo.ageOver18),
    )

    private fun buildMdlClaims(claimNames: Collection<String>?, idToken: OidcUserInfoExtended) = listOfNotNull(
        claim(claimNames, MobileDrivingLicenceDataElements.FAMILY_NAME, idToken.userInfo.familyName),
        claim(claimNames, MobileDrivingLicenceDataElements.GIVEN_NAME, idToken.userInfo.givenName),
        claim(claimNames, MobileDrivingLicenceDataElements.BIRTH_DATE, idToken.dateOfBirth),
        claim(claimNames, MobileDrivingLicenceDataElements.ISSUE_DATE, LocalDate.parse("2023-01-01")),
        claim(claimNames, MobileDrivingLicenceDataElements.EXPIRY_DATE, LocalDate.parse("2025-12-31")),
        claim(claimNames, MobileDrivingLicenceDataElements.DOCUMENT_NUMBER, "123456" + Random.nextLong(1000, 9999)),
        claim(claimNames, MobileDrivingLicenceDataElements.PORTRAIT, idToken.portrait),
        claim(claimNames, MobileDrivingLicenceDataElements.AGE_OVER_18, idToken.userInfo.ageOver18),
    )

    private fun claim(claimNames: Collection<String>?, key: String, value: Any?) =
        if (claimNames.isNullOrContains(key) && value != null) ClaimToBeIssued(key, value.encodeIfNeeded()) else null

    private fun Collection<String>?.isNullOrContains(name: String) =
        this == null || contains(name)

    private val OidcUserInfoExtended.bpk: String
        get() = userInfo.subject // TODO this is not correct

    private val OidcUserInfoExtended.dateOfBirth
        get() = userInfo.birthDate?.let { LocalDate.parse(it) }

    private val OidcUserInfoExtended.portrait: ByteArray?
        get() = userInfo.picture?.decodeToByteArray(Base64())

    private val OidcUserInfoExtended.mainAddress: String?
        get() = userInfo.address?.formatted

}

@OptIn(ExperimentalEncodingApi::class)
private fun Any.encodeIfNeeded() = if (this is ByteArray) kotlin.io.encoding.Base64.encode(this) else this
