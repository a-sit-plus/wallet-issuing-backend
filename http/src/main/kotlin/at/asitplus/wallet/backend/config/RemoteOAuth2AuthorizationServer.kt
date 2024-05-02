package at.asitplus.wallet.backend.config

import at.asitplus.KmmResult
import at.asitplus.KmmResult.Companion.wrap
import at.asitplus.wallet.lib.oidvci.OAuth2AuthorizationServer
import at.asitplus.wallet.lib.oidvci.OidcUserInfo

class RemoteOAuth2AuthorizationServer(
    override val publicContext: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val userinfoEndpoint: String
) : OAuth2AuthorizationServer {

    override suspend fun getUserInfo(accessToken: String): KmmResult<OidcUserInfo> {
        return runCatching {
            // GET at `userInfoEndpoint`
            OidcUserInfo("subject")
        }.wrap()
    }

    override suspend fun providePreAuthorizedCode(): String? {
        return null
    }

    override suspend fun verifyAndRemoveClientNonce(nonce: String): Boolean {
        // NOTE: Not sure, how that should work remotely
        return true
    }
}