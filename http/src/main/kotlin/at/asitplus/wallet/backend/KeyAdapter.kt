package at.asitplus.wallet.backend

import at.asitplus.hsmfacade.provider.RemoteKeyStoreLoadParameter
import at.asitplus.wallet.lib.encodeBase64
import at.asitplus.wallet.lib.jws.EcCurve
import at.asitplus.wallet.lib.jws.JsonWebKey
import at.asitplus.wallet.lib.jws.JwsAlgorithm
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.slf4j.LoggerFactory
import org.springframework.core.io.ResourceLoader
import org.springframework.util.StreamUtils
import java.io.StringReader
import java.nio.charset.Charset
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.Security
import java.security.interfaces.ECPublicKey

/**
 * Interface to use different sources of cryptographic keys in the [CryptoServiceAdapter].
 */
interface KeyAdapter {
    val privateKey: PrivateKey
    val publicKey: PublicKey
    val jsonWebKey: JsonWebKey
    val jwsAlgorithm: JwsAlgorithm
    val provider: Provider
}

class KeyFileAdapter(
    config: KeyFileConfiguration,
    resourceLoader: ResourceLoader,
    securityProviderBean: SecurityProviderBean
) : KeyAdapter {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override val privateKey: PrivateKey
    override val publicKey: PublicKey
    override val jwsAlgorithm: JwsAlgorithm
    override val provider = securityProviderBean.provider
    override val jsonWebKey: JsonWebKey

    init {
        val privateKeyString = loadResource(resourceLoader, config.privateKey.toString())
        val privateKeyRead = PEMParser(StringReader(privateKeyString)).readObject()
        privateKey = JcaPEMKeyConverter().getPrivateKey(privateKeyRead as PrivateKeyInfo)
        val publicKeyString = loadResource(resourceLoader, config.publicKey.toString())
        val publicKeyRead = PEMParser(StringReader(publicKeyString)).readObject()
        publicKey = JcaPEMKeyConverter().getPublicKey(publicKeyRead as SubjectPublicKeyInfo)
        require(publicKey is ECPublicKey) { "expected ECPublicKey" }
        val ecCurve = EcCurve.SECP_256_R_1
        jwsAlgorithm = JwsAlgorithm.ES256
        jsonWebKey = JsonWebKey.fromJcaKey(publicKey, ecCurve)!!
        log.info("Loaded public key: '{}'", publicKey.encoded.encodeBase64())
    }

    private fun loadResource(resourceLoader: ResourceLoader, path: String) =
        StreamUtils.copyToString(resourceLoader.getResource(path).inputStream, Charset.defaultCharset())

}


class KeyStoreAdapter(
    config: KeyStoreConfiguration,
    securityProviderBean: SecurityProviderBean,
) : KeyAdapter {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override val privateKey: PrivateKey
    override val publicKey: PublicKey
    override val jwsAlgorithm: JwsAlgorithm
    override val provider: Provider = config.provider?.let { Security.getProvider(it) } ?: securityProviderBean.provider
    override val jsonWebKey: JsonWebKey

    init {
        val keyStore = KeyStore.getInstance(config.type, provider)
        keyStore.load(config.path.toURL().openStream(), config.password?.toCharArray() ?: charArrayOf())
        privateKey = keyStore.getKey(config.alias, config.aliasPassword?.toCharArray() ?: charArrayOf()) as PrivateKey
        publicKey = keyStore.getCertificate(config.alias).publicKey
        require(publicKey is ECPublicKey) { "expected ECPublicKey" }
        val ecCurve = EcCurve.SECP_256_R_1
        jwsAlgorithm = JwsAlgorithm.ES256
        jsonWebKey = JsonWebKey.fromJcaKey(publicKey, ecCurve)!!
        log.info("Loaded public key: '{}'", publicKey.encoded.encodeBase64())
    }

}

class HsmFacadeAdapter(
    config: KeyHsmFacadeConfiguration,
    securityProviderBean: SecurityProviderBean,
) : KeyAdapter {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override val privateKey: PrivateKey
    override val publicKey: PublicKey
    override val jwsAlgorithm: JwsAlgorithm
    override val provider: Provider = securityProviderBean.provider
    override val jsonWebKey: JsonWebKey

    init {
        val keyStore = KeyStore.getInstance("RemoteKeyStore", securityProviderBean.provider)
        keyStore.load(RemoteKeyStoreLoadParameter(config.keyStoreName!!))
        privateKey = keyStore.getKey(config.keyStoreAlias!!, null) as PrivateKey
        publicKey = keyStore.getCertificate(config.keyStoreAlias).publicKey
        require(publicKey is ECPublicKey) { "expected ECPublicKey" }
        val ecCurve = EcCurve.SECP_256_R_1
        jwsAlgorithm = JwsAlgorithm.ES256
        jsonWebKey = JsonWebKey.fromJcaKey(publicKey, ecCurve)!!
        log.info("Loaded public key: '{}'", publicKey.encoded.encodeBase64())
    }

}

class RandomKeyAdapter() : KeyAdapter {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override val privateKey: PrivateKey
    override val publicKey: PublicKey
    override val jwsAlgorithm: JwsAlgorithm
    override val provider: Provider = BouncyCastleProvider().also { Security.addProvider(it) }
    override val jsonWebKey: JsonWebKey

    init {
        val keyPair = KeyPairGenerator.getInstance("EC", provider).also { it.initialize(256) }.generateKeyPair()
        privateKey = keyPair.private
        publicKey = keyPair.public
        val ecCurve = EcCurve.SECP_256_R_1
        jwsAlgorithm = JwsAlgorithm.ES256
        jsonWebKey = JsonWebKey.fromJcaKey(keyPair.public as ECPublicKey, ecCurve)!!
        log.info("Generated new key pair with public key: '{}'", keyPair.public.encoded.encodeBase64())
    }

}