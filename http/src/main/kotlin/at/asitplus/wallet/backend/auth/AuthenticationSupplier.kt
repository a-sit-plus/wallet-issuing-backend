package at.asitplus.wallet.backend.auth

import at.asitplus.openid.OidcAddressClaim
import at.asitplus.openid.OidcUserInfo
import at.asitplus.openid.OidcUserInfoExtended
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser


interface AuthenticationSupplier {

    fun getCurrentUserOidcDetails(): OidcUserInfoExtended?

}

class SpringSecurityAuthenticationSupplier : AuthenticationSupplier {

    override fun getCurrentUserOidcDetails(): OidcUserInfoExtended? {
        return loadOidcIdToken()?.toOidcUserInfoExtended()
    }

    private fun loadOidcIdToken(): OidcIdToken? {
        val authn = SecurityContextHolder.getContext()?.authentication
            ?: return null
        if (authn is OidcUser)
            return authn.idToken
        val principal = authn.principal
        if (principal is OidcUser)
            return principal.idToken
        return null
    }

    private fun OidcIdToken.toOidcUserInfoExtended() = OidcUserInfoExtended(
        OidcUserInfo(
            subject = subject,
            name = fullName,
            givenName = givenName,
            familyName = familyName,
            middleName = middleName,
            nickname = nickName,
            preferredUsername = preferredUsername,
            profile = profile,
            picture = picture,
            website = website,
            email = email,
            emailVerified = emailVerified,
            gender = gender,
            birthDate = birthdate,
            timezone = zoneInfo,
            locale = locale,
            phoneNumber = phoneNumber,
            phoneNumberVerified = phoneNumberVerified,
            address = OidcAddressClaim(
                formatted = address.formatted,
                street = address.streetAddress,
                locality = address.locality,
                region = address.region,
                postalCode = address.postalCode,
                country = address.country,
            ),
            ageOver18 = getClaimAsBoolean("age_over_18"),
            updatedAt = updatedAt?.toKotlinInstant(),
        ),
        JsonObject(claims.map { m ->
            m.key to runCatching { Json.parseToJsonElement(m.value.toString()) }
                .getOrElse { JsonPrimitive(m.value.toString()) }
        }.toMap())
    )
}