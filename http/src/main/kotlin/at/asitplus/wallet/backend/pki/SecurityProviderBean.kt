package at.asitplus.wallet.backend.pki

import at.asitplus.hsmfacade.provider.HsmFacadeProvider
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import io.github.aakira.napier.Napier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.springframework.core.io.ResourceLoader
import java.security.Provider
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate


/**
 * Loads the [Provider] for cryptographic operations,
 * e.g. a connection to a HsmFacade service or fallback [BouncyCastleProvider].
 */
class SecurityProviderBean(
    configurationProperties: BackendConfigurationProperties,
    resourceLoader: ResourceLoader,
) {
    val provider: Provider

    init {
        if (configurationProperties.hsmfacade.enabled) {
            Napier.i("Loading HSM Facade Provider")
            val config = configurationProperties.hsmfacade
            val hsmFacadeProvider = HsmFacadeProvider.instance
            if (!hsmFacadeProvider.isInitialized) {
                val rootCert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(resourceLoader.getResource(config.rootCertificate!!.toString()).inputStream)
                        as X509Certificate
                hsmFacadeProvider.init(
                    rootCert,
                    config.username!!,
                    config.password!!,
                    config.hostname!!,
                    config.port!!,
                    config.timeout
                )
            }
            provider = hsmFacadeProvider.also {
                Security.addProvider(it)
            }
        } else {
            Napier.i("Loading Bouncycastle Provider")
            provider = BouncyCastleProvider().also {
                Security.addProvider(it)
            }
        }
    }

}