package at.asitplus.wallet.backend.auth

import org.springframework.session.web.http.HeaderHttpSessionIdResolver

object WebSecurityConstants {
    /**
     * Assigned when client authenticates with "external nonce",
     * i.e. Wallet App scans a QR Code containing that nonce.
     */
    const val AUTHORITY_PUPIL = "PUPIL"

    /**
     * Assigned when client authenticates with their device binding,
     * i.e. challenge-response signed with valid binding key.
     */
    const val AUTHORITY_DEVICE_BINDING = "DEVICE_BINDING"

    /**
     * Assigned when client authenticates with API Key,
     * i.e. another backend service sends that API key in a header.
     */
    const val AUTHORITY_REVOCATION = "REVOCATION"

    const val PREFIX_RESPONSE = "Response "

    const val X_API_KEY = "X-API-Key"
    const val X_AUTH_EXT_NONCE = "X-Auth-ExtNonce"

    /**
     * from [HeaderHttpSessionIdResolver]
     */
    const val X_AUTH_TOKEN = "X-Auth-Token"

    /**
     * Assigned when client authenticates using OIDC,
     * i.e. Wallet App defers to ID Austria App
     */
    const val AUTHORITY_OIDC = "OIDC_IDA"
}
