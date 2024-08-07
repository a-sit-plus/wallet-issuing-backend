package at.asitplus.wallet.backend.config

import at.asitplus.wallet.backend.auth.AuthenticationSupplier
import at.asitplus.wallet.lib.oidc.AuthenticationRequestParameters
import at.asitplus.wallet.lib.oidvci.OAuth2DataProvider
import at.asitplus.wallet.lib.oidvci.OidcUserInfoExtended
import io.github.aakira.napier.Napier

class PreAuthnOAuth2DataProvider(
    private val authenticationSupplier: AuthenticationSupplier
) : OAuth2DataProvider {

    override suspend fun loadUserInfo(request: AuthenticationRequestParameters?) =
        authenticationSupplier.getCurrentUserOidcDetails()
            .also { Napier.d("loadUserInfo: output $it") }

}
