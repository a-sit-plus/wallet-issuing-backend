package at.asitplus.wallet.backend.pki

import io.github.aakira.napier.Napier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Provider
import java.security.Security


class SecurityProviderBean {
    val provider: Provider

    init {
        Napier.i("Loading Bouncycastle Provider")
        provider = BouncyCastleProvider().also {
            Security.addProvider(it)
        }
    }
}

