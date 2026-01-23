package at.asitplus.wallet.backend

import org.springframework.stereotype.Component

@Component("PathsBean") // for access from Thymeleaf
object Paths {
    const val LoginUrl = "/login"
    const val RevocationUrl = "/revocation"
    const val LogoutUrl = "/logout"
    const val RevokeUrl = "/revoke"
    const val StatusUrl = "/status"
    const val ParUrl = "/par"
    const val NonceUrl = "/nonce"
    const val AuthorizeUrl = "/authorize"
    const val TokenUrl = "/token"
    const val CredentialUrl = "/credential"
    const val OfferUrl = "/offer"

    object Transaction {
        const val ResultUrl = "/transaction/result"
        const val GetUrl = "/transaction/get"
    }
    object Credentials {
        const val StatusUrl = "/credentials/status"

        object Status {
            const val CurrentUrl = "/credentials/status/current"
        }
    }
}