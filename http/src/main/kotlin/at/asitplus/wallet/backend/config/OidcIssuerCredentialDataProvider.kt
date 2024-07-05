package at.asitplus.wallet.backend.config

import at.asitplus.KmmResult
import at.asitplus.crypto.datatypes.CryptoPublicKey
import at.asitplus.crypto.datatypes.jws.toJsonWebKey
import at.asitplus.wallet.cor.CertificateOfResidenceDataElements
import at.asitplus.wallet.cor.CertificateOfResidenceScheme
import at.asitplus.wallet.cor.ResidenceAddress
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.idaustria.IdAustriaCredential
import at.asitplus.wallet.idaustria.IdAustriaScheme
import at.asitplus.wallet.idaustria.IdAustriaScheme.Attributes
import at.asitplus.wallet.lib.agent.ClaimToBeIssued
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.iso.IssuerSignedItem
import at.asitplus.wallet.lib.oidvci.OidcUserInfoExtended
import at.asitplus.wallet.mdl.DrivingPrivilege
import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
import at.asitplus.wallet.por.PowerOfRepresentationDataElements
import at.asitplus.wallet.por.PowerOfRepresentationScheme
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
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
    ): KmmResult<List<CredentialToBeIssued>> = at.asitplus.catching {
        val issuance = Clock.System.now()
        val expiration = issuance + lifetime
        Napier.v("getCredential for $credentialScheme and ${subjectPublicKey.didEncoded} in $representation, $claimNames")
        Napier.v("getCredential user is $userInfo")
        when (representation) {
            ConstantIndex.CredentialRepresentation.PLAIN_JWT -> when (credentialScheme) {
                IdAustriaScheme -> idaVcJwt(subjectPublicKey, userInfo, expiration)
                EuPidScheme -> eupidVcJwt(subjectPublicKey, userInfo, issuance, expiration)
                else -> null
            }

            ConstantIndex.CredentialRepresentation.SD_JWT -> when (credentialScheme) {
                IdAustriaScheme -> idaVcSd(claimNames, userInfo, expiration)
                EuPidScheme -> eupidVcSd(claimNames, userInfo, issuance, expiration)
                PowerOfRepresentationScheme -> eupidVcSd(claimNames, userInfo, issuance, expiration)
                CertificateOfResidenceScheme -> eupidVcSd(claimNames, userInfo, issuance, expiration)
                else -> null
            }

            ConstantIndex.CredentialRepresentation.ISO_MDOC -> when (credentialScheme) {
                IdAustriaScheme -> idaIso(claimNames, userInfo, expiration)
                EuPidScheme -> eupidIso(claimNames, userInfo, issuance, expiration)
                MobileDrivingLicenceScheme -> mdlIso(claimNames, userInfo, expiration)
                else -> null
            }
        }?.let { listOf(it) } ?: listOf()
    }

    private fun idaIso(
        claimNames: Collection<String>?,
        idToken: OidcUserInfoExtended,
        expiration: Instant
    ) = CredentialToBeIssued.Iso(
        issuerSignedItems = buildIdaClaims(claimNames, idToken).mapIndexed(::buildIssuerSignedItem)
            .also { Napier.v("idaIso returns $it") },
        expiration = expiration
    )

    private fun eupidIso(
        claimNames: Collection<String>?,
        idToken: OidcUserInfoExtended,
        issuance: Instant,
        expiration: Instant
    ) = CredentialToBeIssued.Iso(
        issuerSignedItems = buildEupidClaims(
            claimNames,
            idToken,
            issuance,
            expiration
        ).mapIndexed(::buildIssuerSignedItem)
            .also { Napier.v("eupidIso returns $it") },
        expiration = expiration
    )

    private fun mdlIso(
        claimNames: Collection<String>?,
        idToken: OidcUserInfoExtended,
        expiration: Instant
    ) = CredentialToBeIssued.Iso(
        issuerSignedItems = buildMdlClaims(claimNames, idToken).mapIndexed(::buildIssuerSignedItem)
            .also { Napier.v("mdlIso returns $it") },
        expiration = expiration
    )

    private fun buildIssuerSignedItem(
        index: Int,
        claimToBeIssued: ClaimToBeIssued
    ) = IssuerSignedItem(
        digestId = index.toUInt(),
        random = Random.nextBytes(16),
        elementIdentifier = claimToBeIssued.name,
        elementValue = claimToBeIssued.value
    )

    private fun idaVcSd(
        claimNames: Collection<String>?,
        idToken: OidcUserInfoExtended,
        expiration: Instant
    ) = CredentialToBeIssued.VcSd(
        claims = buildIdaClaims(claimNames, idToken)
            .also { Napier.v("idaVcSd returns $it") },
        expiration = expiration
    )

    private fun eupidVcSd(
        claimNames: Collection<String>?,
        idToken: OidcUserInfoExtended,
        issuance: Instant,
        expiration: Instant
    ) = CredentialToBeIssued.VcSd(
        claims = buildEupidClaims(claimNames, idToken, issuance, expiration)
            .also { Napier.v("eupidVcSd returns $it") },
        expiration = expiration
    )

    private fun porVcSd(
        claimNames: Collection<String>?,
        idToken: OidcUserInfoExtended,
        issuance: Instant,
        expiration: Instant
    ) = CredentialToBeIssued.VcSd(
        claims = buildPorClaims(claimNames, idToken, issuance, expiration)
            .also { Napier.v("porVcSd returns $it") },
        expiration = expiration
    )

    private fun corVcSd(
        claimNames: Collection<String>?,
        idToken: OidcUserInfoExtended,
        issuance: Instant,
        expiration: Instant
    ) = CredentialToBeIssued.VcSd(
        claims = buildCorClaims(claimNames, idToken, issuance, expiration)
            .also { Napier.v("corVcSd returns $it") },
        expiration = expiration
    )

    private fun idaVcJwt(
        subjectPublicKey: CryptoPublicKey,
        idToken: OidcUserInfoExtended,
        expiration: Instant
    ) = CredentialToBeIssued.VcJwt(
        subject = IdAustriaCredential(
            id = subjectPublicKey.toJsonWebKey().identifier,
            bpk = idToken.bpk,
            firstname = idToken.userInfo.givenName ?: "N/A",
            lastname = idToken.userInfo.familyName ?: "N/A",
            dateOfBirth = idToken.dateOfBirth ?: LocalDate.fromEpochDays(0),
            portrait = idToken.portrait,
            mainAddress = idToken.mainAddress,
            ageOver14 = idToken.ageOver14,
            ageOver16 = idToken.ageOver16,
            ageOver18 = idToken.ageOver18,
            ageOver21 = idToken.ageOver21,
        ).also { Napier.v("idaVcJwt returns $it") },
        expiration = expiration,
    )

    private fun eupidVcJwt(
        subjectPublicKey: CryptoPublicKey,
        idToken: OidcUserInfoExtended,
        issuance: Instant,
        expiration: Instant
    ) = CredentialToBeIssued.VcJwt(
        subject = EuPidCredential(
            id = subjectPublicKey.toJsonWebKey().identifier,
            familyName = idToken.userInfo.familyName ?: "N/A",
            givenName = idToken.userInfo.givenName ?: "N/A",
            birthDate = idToken.dateOfBirth ?: LocalDate.fromEpochDays(0),
            ageOver18 = idToken.ageOver18,
            issuanceDate = issuance,
            expiryDate = expiration,
            issuingAuthority = "Miniwahr",
            issuingCountry = "AT",
        ).also { Napier.v("eupidVcJwt returns $it") },
        expiration = expiration,
    )

    private fun buildIdaClaims(claimNames: Collection<String>?, idToken: OidcUserInfoExtended) = listOfNotNull(
        claim(claimNames, Attributes.BPK, idToken.bpk),
        claim(claimNames, Attributes.FIRSTNAME, idToken.userInfo.givenName),
        claim(claimNames, Attributes.LASTNAME, idToken.userInfo.familyName),
        claim(claimNames, Attributes.DATE_OF_BIRTH, idToken.dateOfBirth),
        claim(claimNames, Attributes.PORTRAIT, idToken.portrait),
        claim(claimNames, Attributes.MAIN_ADDRESS, idToken.mainAddress),
        claim(claimNames, Attributes.AGE_OVER_18, idToken.ageOver18),
    )

    private fun buildEupidClaims(
        claimNames: Collection<String>?,
        idToken: OidcUserInfoExtended,
        issuance: Instant,
        expiration: Instant
    ) = listOfNotNull(
        claim(claimNames, EuPidScheme.Attributes.FAMILY_NAME, idToken.userInfo.familyName),
        claim(claimNames, EuPidScheme.Attributes.GIVEN_NAME, idToken.userInfo.givenName),
        claim(claimNames, EuPidScheme.Attributes.BIRTH_DATE, idToken.dateOfBirth),
        claim(claimNames, EuPidScheme.Attributes.AGE_OVER_18, idToken.ageOver18),
        claim(claimNames, EuPidScheme.Attributes.ISSUANCE_DATE, issuance),
        claim(claimNames, EuPidScheme.Attributes.EXPIRY_DATE, expiration),
        claim(claimNames, EuPidScheme.Attributes.ISSUING_AUTHORITY, "Miniwahr"),
        claim(claimNames, EuPidScheme.Attributes.ISSUING_COUNTRY, "AT"),
    )

    private fun buildPorClaims(
        claimNames: Collection<String>?,
        idToken: OidcUserInfoExtended,
        issuance: Instant,
        expiration: Instant
    ) = listOfNotNull(
        claim(claimNames, PowerOfRepresentationDataElements.LEGAL_PERSON_IDENTIFIER, idToken.legalPersonIdentifier),
        claim(claimNames, PowerOfRepresentationDataElements.LEGAL_NAME, idToken.legalName),
        claim(claimNames, PowerOfRepresentationDataElements.FULL_POWERS, true),
        claim(claimNames, PowerOfRepresentationDataElements.EFFECTIVE_FROM_DATE, issuance),
        claim(claimNames, PowerOfRepresentationDataElements.ISSUANCE_DATE, issuance),
        claim(claimNames, PowerOfRepresentationDataElements.EXPIRY_DATE, expiration),
        claim(claimNames, PowerOfRepresentationDataElements.ISSUING_AUTHORITY, "Miniwahr"),
        claim(claimNames, PowerOfRepresentationDataElements.ISSUING_COUNTRY, "AT"),
    )

    private fun buildCorClaims(
        claimNames: Collection<String>?,
        idToken: OidcUserInfoExtended,
        issuance: Instant,
        expiration: Instant
    ) = listOfNotNull(
        claim(claimNames, CertificateOfResidenceDataElements.FAMILY_NAME, idToken.userInfo.familyName),
        claim(claimNames, CertificateOfResidenceDataElements.GIVEN_NAME, idToken.userInfo.givenName),
        claim(claimNames, CertificateOfResidenceDataElements.BIRTH_DATE, idToken.dateOfBirth),
        claim(claimNames, CertificateOfResidenceDataElements.RESIDENCE_ADDRESS, idToken.residenceAddress),
        claim(claimNames, CertificateOfResidenceDataElements.ISSUANCE_DATE, issuance),
        claim(claimNames, CertificateOfResidenceDataElements.EXPIRY_DATE, expiration),
        claim(claimNames, CertificateOfResidenceDataElements.ISSUING_AUTHORITY, "Miniwahr"),
        claim(claimNames, CertificateOfResidenceDataElements.ISSUING_COUNTRY, "AT"),
    )

    private fun buildMdlClaims(claimNames: Collection<String>?, idToken: OidcUserInfoExtended) = listOfNotNull(
        claim(claimNames, MobileDrivingLicenceDataElements.FAMILY_NAME, idToken.userInfo.familyName),
        claim(claimNames, MobileDrivingLicenceDataElements.GIVEN_NAME, idToken.userInfo.givenName),
        claim(claimNames, MobileDrivingLicenceDataElements.BIRTH_DATE, idToken.dateOfBirth),
        claim(claimNames, MobileDrivingLicenceDataElements.ISSUE_DATE, LocalDate.parse("2023-01-01")),
        claim(claimNames, MobileDrivingLicenceDataElements.ISSUING_AUTHORITY, "Miniwahr"),
        claim(claimNames, MobileDrivingLicenceDataElements.ISSUING_COUNTRY, "AT"),
        claim(claimNames, MobileDrivingLicenceDataElements.UN_DISTINGUISHING_SIGN, "A"),
        claim(
            claimNames,
            MobileDrivingLicenceDataElements.DRIVING_PRIVILEGES,
            arrayOf(DrivingPrivilege("B", LocalDate.parse("2023-01-01"), LocalDate.parse("2025-12-31")))
        ),
        claim(claimNames, MobileDrivingLicenceDataElements.EXPIRY_DATE, LocalDate.parse("2025-12-31")),
        claim(claimNames, MobileDrivingLicenceDataElements.DOCUMENT_NUMBER, "123456" + Random.nextLong(1000, 9999)),
        claim(claimNames, MobileDrivingLicenceDataElements.PORTRAIT, idToken.portrait),
        claim(claimNames, MobileDrivingLicenceDataElements.AGE_OVER_18, idToken.ageOver18),
    )

    private fun claim(claimNames: Collection<String>?, key: String, value: Any?) =
        if (claimNames.isNullOrContains(key) && value != null) ClaimToBeIssued(key, value.encodeIfNeeded()) else null

    private fun Collection<String>?.isNullOrContains(name: String) =
        this == null || contains(name)

    private val OidcUserInfoExtended.bpk: String
        get() = getValue("urn:pvpgvat:oidc.bpk") ?: userInfo.subject

    private val OidcUserInfoExtended.dateOfBirth
        get() = userInfo.birthDate?.let { LocalDate.parse(it) }

    private val OidcUserInfoExtended.ageOver14
        get() = getValue("org.iso.18013.5.1:age_over_14")?.let { it.toBoolean() }
            ?: ageOver16

    private val OidcUserInfoExtended.ageOver16
        get() = getValue("org.iso.18013.5.1:age_over_16")?.let { it.toBoolean() }
            ?: ageOver18

    private val OidcUserInfoExtended.ageOver18: Boolean?
        get() = userInfo.ageOver18
            ?: getValue("org.iso.18013.5.1:age_over_18")?.let { it.toBoolean() }

    private val OidcUserInfoExtended.ageOver21: Boolean?
        get() = userInfo.ageOver18
            ?: getValue("org.iso.18013.5.1:age_over_21")?.let { it.toBoolean() }

    private val OidcUserInfoExtended.portrait: ByteArray?
        get() = userInfo.picture?.decodeToByteArray(Base64())
            ?: getValue("org.iso.18013.5.1:portrait")?.decodeToByteArray(Base64())

    private val OidcUserInfoExtended.mainAddress: String?
        get() = userInfo.address?.formatted
            ?: getValue("urn:eidgvat:attributes.mainAddress")

    private val OidcUserInfoExtended.legalName: String
        get() = getValue("urn:oid:1.2.40.0.10.2.1.1.261.84")
            ?: userInfo.name ?: userInfo.familyName ?: userInfo.subject

    private val OidcUserInfoExtended.legalPersonIdentifier: String
        get() = getValue("urn:oid:1.2.40.0.10.2.1.1.261.100")
            ?: userInfo.name ?: userInfo.familyName ?: userInfo.subject

    private fun OidcUserInfoExtended.getValue(key: String): String? {
        val element = jsonObject[key]
        if (element is JsonPrimitive) {
            return element.content
        }
        return element?.toString()
    }

    private val OidcUserInfoExtended.residenceAddress: String
        get() = Json.encodeToString(
            ResidenceAddress(
                thoroughfare = userInfo.address?.street ?: "Riverside Drive",
                locatorDesignator = "1640",
                postCode = userInfo.address?.postalCode ?: "90210",
                postName = userInfo.address?.locality ?: "Hill Valley",
                adminUnitLevel1 = "US",
                adminUnitLevel2 = "CA",
                fullAddress = mainAddress,
            )
        )
}

@OptIn(ExperimentalEncodingApi::class)
private fun Any.encodeIfNeeded() = if (this is ByteArray) kotlin.io.encoding.Base64.encode(this) else this
