package at.asitplus.wallet.backend.auth

import at.asitplus.openid.OidcAddressClaim
import at.asitplus.openid.OidcUserInfo
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.wallet.backend.controller.OpenId4VpUser
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.User
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import kotlin.time.toKotlinInstant

object SpringSecurityAuthenticationSupplier {

    fun toOidcUserInfoExtended(authn: Authentication?): OidcUserInfoExtended? {
        if (authn is OidcUser)
            return authn.idToken.toOidcUserInfoExtended()
        val principal = authn?.principal
        Napier.i("toOidcUserInfoExtended called with $principal")
        if (principal is OidcUser)
            return principal.idToken.toOidcUserInfoExtended()
        if (principal is OpenId4VpUser)
            return principal.toOidcUserInfoExtended()
        if (principal is User)
            return fakeOidcUserInfoExtended()
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
            m.key to JsonPrimitive(m.value.toString())
        }.toMap())
    )

    private fun OpenId4VpUser.toOidcUserInfoExtended() = OidcUserInfoExtended(
        userInfo = OidcUserInfo(
            subject = id,
            name = "$firstname $lastname",
            givenName = firstname,
            familyName = lastname,
            picture = imageDataBase64?.removePrefix("data:image;base64,"),
        ),
        jsonObject = credentials.firstOrNull()?.allFields ?: JsonObject(emptyMap())
    )

    private fun fakeOidcUserInfoExtended(): OidcUserInfoExtended =
        OidcUserInfoExtended.fromOidcUserInfo(
            OidcUserInfo(
                subject = "fake",
                name = "Bernd Abt",
                givenName = "Bernd",
                familyName = "Abt",
                gender = "male",
                birthDate = "1962-10-26",
                picture = "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",
                ageOver18 = true,
            )
        ).getOrThrow()
}