package at.asitplus.wallet.backend.config

import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.lib.oidc.AuthenticationRequestParameters
import at.asitplus.wallet.lib.oidvci.OAuth2DataProvider
import at.asitplus.wallet.lib.oidvci.OidcAddressClaim
import at.asitplus.wallet.lib.oidvci.OidcUserInfo
import at.asitplus.wallet.lib.oidvci.OidcUserInfoExtended
import io.github.aakira.napier.Napier
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class PreAuthnOAuth2DataProvider(
    private val authenticationSupplier: AuthenticationSupplier
) : OAuth2DataProvider {

    override suspend fun loadUserInfo(request: AuthenticationRequestParameters?): OidcUserInfoExtended? {
        val idToken = authenticationSupplier.getCurrentUserOidcDetails()
            ?: return null
        val oidcUserInfo = OidcUserInfo(
            subject = idToken.subject,
            name = idToken.fullName,
            givenName = idToken.givenName,
            familyName = idToken.familyName,
            middleName = idToken.middleName,
            nickname = idToken.nickName,
            preferredUsername = idToken.preferredUsername,
            profile = idToken.profile,
            picture = idToken.picture,
            website = idToken.website,
            email = idToken.email,
            emailVerified = idToken.emailVerified,
            gender = idToken.gender,
            birthDate = idToken.birthdate,
            timezone = idToken.zoneInfo,
            locale = idToken.locale,
            phoneNumber = idToken.phoneNumber,
            phoneNumberVerified = idToken.phoneNumberVerified,
            address = OidcAddressClaim(
                formatted = idToken.address.formatted,
                street = idToken.address.streetAddress,
                locality = idToken.address.locality,
                region = idToken.address.region,
                postalCode = idToken.address.postalCode,
                country = idToken.address.country,
            ),
            ageOver18 = idToken.getClaimAsBoolean("age_over_18"),
            updatedAt = idToken.updatedAt?.toKotlinInstant(),
        )
        return OidcUserInfoExtended(
            oidcUserInfo,
            JsonObject(idToken.claims.map { m ->
                m.key to runCatching { Json.parseToJsonElement(m.value.toString()) }
                    .getOrElse { JsonPrimitive(m.value.toString()) }
            }.toMap())
        ).also { Napier.d("loadUserInfo: output $it") }
    }

}
