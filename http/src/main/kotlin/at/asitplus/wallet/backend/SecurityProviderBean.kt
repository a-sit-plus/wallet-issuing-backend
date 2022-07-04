package at.asitplus.wallet.backend

import at.asitplus.hsmfacade.provider.HsmFacadeProvider
import at.asitplus.wallet.remotecrypto.RemoteCryptoProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
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

    private val log = LoggerFactory.getLogger(this.javaClass)

    val provider: Provider

    init {
        if (configurationProperties.hsmfacade.enabled) {
            log.info("Loading HSM Facade Provider")
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
        } else if (configurationProperties.remoteCrypto.enabled) {
            log.info("Loading Remote Crypto Provider")
            val config = configurationProperties.remoteCrypto
            val remoteCryptoProvider = RemoteCryptoProvider.instance
            if (!remoteCryptoProvider.isInitialized) {
                remoteCryptoProvider.init(
                    config.username!!,
                    config.password!!,
                    config.hostname!!,
                    config.port!!,
                )
            }
            provider = remoteCryptoProvider.also {
                Security.addProvider(it)
            }
        } else {
            log.info("Loading Bouncycastle Provider")
            provider = BouncyCastleProvider().also {
                Security.addProvider(it)
            }
        }
    }

}