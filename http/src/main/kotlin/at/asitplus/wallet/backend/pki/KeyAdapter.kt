package at.asitplus.wallet.backend.pki

import at.asitplus.hsmfacade.provider.RemoteKeyStoreLoadParameter
import at.asitplus.wallet.backend.config.KeyFileConfiguration
import at.asitplus.wallet.backend.config.KeyHsmFacadeConfiguration
import at.asitplus.wallet.backend.config.KeyRemoteCryptoConfiguration
import at.asitplus.wallet.backend.config.KeyStoreConfiguration
import at.asitplus.wallet.backend.service.fromJcaKey
import at.asitplus.wallet.lib.encodeBase64
import at.asitplus.wallet.lib.jws.EcCurve
import at.asitplus.wallet.lib.jws.JsonWebKey
import at.asitplus.wallet.lib.jws.JwsAlgorithm
import at.asitplus.wallet.remotecrypto.EcRemoteKeyParameterSpec
import io.github.aakira.napier.Napier
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.springframework.core.io.ResourceLoader
import org.springframework.util.StreamUtils
import java.io.StringReader
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.security.*
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey

/**
 * Interface to use different sources of cryptographic keys in the [CryptoServiceAdapter].
 */
interface KeyAdapter {
    val privateKey: PrivateKey
    val publicKey: PublicKey
    val certificate: X509Certificate?
    val jsonWebKey: JsonWebKey
    val jwsAlgorithm: JwsAlgorithm
    val provider: Provider
}

class KeyFileAdapter(
    config: KeyFileConfiguration,
    resourceLoader: ResourceLoader,
    securityProviderBean: SecurityProviderBean
) : KeyAdapter {


    override val privateKey: PrivateKey
    override val certificate: X509Certificate?
    override val publicKey: PublicKey
    override val jwsAlgorithm: JwsAlgorithm
    override val provider = securityProviderBean.provider
    override val jsonWebKey: JsonWebKey

    init {
        val privateKeyString = loadResource(resourceLoader, config.privateKey.toString())
        val privateKeyRead = PEMParser(StringReader(privateKeyString)).readObject()
        privateKey = JcaPEMKeyConverter().getPrivateKey(privateKeyRead as PrivateKeyInfo)

        val (k, c) = loadCertOrPubKey(config.publicKey, config.certificate, resourceLoader)
        require(k is ECPublicKey) { "expected ECPublicKey" }

        publicKey = k
        certificate = c

        val ecCurve = EcCurve.SECP_256_R_1
        jwsAlgorithm = JwsAlgorithm.ES256
        jsonWebKey = JsonWebKey.fromJcaKey(publicKey, ecCurve)!!
        Napier.i("Loaded public key: '${publicKey.encoded.encodeBase64()}'")
    }

}


class KeyStoreAdapter(
    override val provider: Provider,
    url: URL,
    type: String,
    password: String?,
    alias: String,
    aliasPassword: String?
) : KeyAdapter {

    constructor(
        config: KeyStoreConfiguration,
        securityProviderBean: SecurityProviderBean,
    ) : this(config.provider?.let { Security.getProvider(it) } ?: securityProviderBean.provider,
        config.path.toURL(), config.type, config.password, config.alias, config.aliasPassword)


    override val privateKey: PrivateKey
    override val publicKey: PublicKey
    override val certificate: X509Certificate
    override val jwsAlgorithm: JwsAlgorithm

    override val jsonWebKey: JsonWebKey

    init {
        val keyStore = KeyStore.getInstance(type, provider)
        keyStore.load(
            url.openStream(),
            password?.toCharArray() ?: charArrayOf()
        )
        privateKey = keyStore.getKey(
            alias,
            aliasPassword?.toCharArray() ?: charArrayOf()
        ) as PrivateKey
        certificate = keyStore.getCertificate(alias) as X509Certificate
        publicKey = certificate.publicKey
        require(publicKey is ECPublicKey) { "expected ECPublicKey" }
        val ecCurve = EcCurve.SECP_256_R_1
        jwsAlgorithm = JwsAlgorithm.ES256
        jsonWebKey = JsonWebKey.fromJcaKey(publicKey, ecCurve)!!
        Napier.i("Loaded public key: '${publicKey.encoded.encodeBase64()}'")
    }

}

class HsmFacadeAdapter(
    config: KeyHsmFacadeConfiguration,
    securityProviderBean: SecurityProviderBean,
) : KeyAdapter {


    override val privateKey: PrivateKey
    override val publicKey: PublicKey
    override val certificate: X509Certificate
    override val jwsAlgorithm: JwsAlgorithm
    override val provider: Provider = securityProviderBean.provider
    override val jsonWebKey: JsonWebKey

    init {
        val keyStore = KeyStore.getInstance("RemoteKeyStore", securityProviderBean.provider)
        keyStore.load(RemoteKeyStoreLoadParameter(config.keyStoreName!!))
        privateKey = keyStore.getKey(config.keyStoreAlias!!, null) as PrivateKey
        certificate = keyStore.getCertificate(config.keyStoreAlias) as X509Certificate
        publicKey = certificate.publicKey
        require(publicKey is ECPublicKey) { "expected ECPublicKey" }
        val ecCurve = EcCurve.SECP_256_R_1
        jwsAlgorithm = JwsAlgorithm.ES256
        jsonWebKey = JsonWebKey.fromJcaKey(publicKey, ecCurve)!!
        Napier.i("Loaded public key: '${publicKey.encoded.encodeBase64()}'")
    }

}

class RemoteKeyAdapter(
    config: KeyRemoteCryptoConfiguration,
    resourceLoader: ResourceLoader,
    securityProviderBean: SecurityProviderBean,
) : KeyAdapter {


    override val privateKey: PrivateKey
    override val certificate: X509Certificate?
    override val publicKey: PublicKey
    override val jwsAlgorithm: JwsAlgorithm
    override val provider: Provider = securityProviderBean.provider
    override val jsonWebKey: JsonWebKey

    init {

        val (k, c) = loadCertOrPubKey(config.publicKey, config.certificate, resourceLoader)
        require(k is ECPublicKey) { "expected ECPublicKey" }

        publicKey = k
        certificate = c

        val spec = EcRemoteKeyParameterSpec("secp256r1", config.keyName, pubKey = publicKey)
        val generator = KeyPairGenerator.getInstance("EC", provider).apply { initialize(spec) }
        val keyPair = generator.generateKeyPair()
        privateKey = keyPair.private

        val ecCurve = EcCurve.SECP_256_R_1
        jwsAlgorithm = JwsAlgorithm.ES256
        jsonWebKey = JsonWebKey.fromJcaKey(publicKey, ecCurve)!!
        Napier.i("Loaded remote public key: '${publicKey.encoded.encodeBase64()}'")
    }

}

class RandomKeyAdapter : KeyAdapter {


    override val privateKey: PrivateKey
    override val publicKey: PublicKey
    override val certificate: X509Certificate? = null
    override val jwsAlgorithm: JwsAlgorithm
    override val provider: Provider = BouncyCastleProvider().also { Security.addProvider(it) }
    override val jsonWebKey: JsonWebKey

    init {
        val keyPair = KeyPairGenerator.getInstance("EC", provider).also { it.initialize(256) }
            .generateKeyPair()
        privateKey = keyPair.private
        publicKey = keyPair.public
        val ecCurve = EcCurve.SECP_256_R_1
        jwsAlgorithm = JwsAlgorithm.ES256
        jsonWebKey = JsonWebKey.fromJcaKey(keyPair.public as ECPublicKey, ecCurve)!!
        Napier.i("Generated new key pair with public key: '${keyPair.public.encoded.encodeBase64()}'")
    }
}


private fun loadCertOrPubKey(
    publicKey: URI?,
    certificate: URI?,
    resourceLoader: ResourceLoader
): Pair<PublicKey, X509Certificate?> {
    if (publicKey == null && certificate == null) throw RuntimeException("Neither cert nor public key configured. Set one!")
    if (publicKey != null && certificate != null) throw RuntimeException("Both public key and certificate set. Set either but not both!")
    return (publicKey?.let {
        val publicKeyString = loadResource(resourceLoader, it.toString())
        val publicKeyRead = PEMParser(StringReader(publicKeyString)).readObject()
        JcaPEMKeyConverter().getPublicKey(publicKeyRead as SubjectPublicKeyInfo) to null
    } ?: certificate?.let {
        loadCertificate(resourceLoader, it).let { it.publicKey to it }
    })!!
}


private fun loadResource(resourceLoader: ResourceLoader, path: String) =
    StreamUtils.copyToString(
        resourceLoader.getResource(path).inputStream,
        Charset.defaultCharset()
    )


private fun loadCertificate(resourceLoader: ResourceLoader, src: URI): X509Certificate {
    val pemCert = loadResource(resourceLoader, src.toString())
    return JcaX509CertificateConverter().apply { setProvider("BC") }.getCertificate(
        PEMParser(StringReader(pemCert)).readObject() as X509CertificateHolder
    )
}
