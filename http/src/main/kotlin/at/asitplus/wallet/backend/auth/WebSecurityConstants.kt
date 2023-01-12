package at.asitplus.wallet.backend.auth

import org.springframework.session.web.http.HeaderHttpSessionIdResolver

object WebSecurityConstants {
    const val AUTHORITY_PUPIL = "PUPIL"
    const val AUTHORITY_DEVICE_BINDING = "DEVICE_BINDING"
    const val AUTHORITY_REVOCATION = "REVOCATION"

    const val PREFIX_RESPONSE = "Response "

    const val X_API_KEY = "X-API-Key"
    const val X_AUTH_EXT_NONCE = "X-Auth-ExtNonce"
    /**
     * from [HeaderHttpSessionIdResolver]
     */
    const val X_AUTH_TOKEN = "X-Auth-Token"

    const val AUTHORITY_OIDC_EIDASID = "OIDC_EIDASID"
    const val AUTHORITY_OIDC_PUPIL = "OIDC_PUPIL"
}
