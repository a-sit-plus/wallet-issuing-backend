package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.encodeBase64
import at.asitplus.wallet.lib.jws.EcCurve
import at.asitplus.wallet.lib.jws.JsonWebKey
import at.asitplus.wallet.lib.jws.JwsAlgorithm
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
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
import java.security.interfaces.ECPublicKey

/**
 * Interface to use different sources of cryptographic keys in the [FileCryptoService].
 */
interface KeyAdapter {
    val privateKey: PrivateKey
    val jsonWebKey: JsonWebKey
    val jwsAlgorithm: JwsAlgorithm
    val provider: String
}

class KeyFileAdapter(
    config: KeyFileConfiguration,
    resourceLoader: ResourceLoader
) : KeyAdapter {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override val privateKey: PrivateKey
    override val jwsAlgorithm: JwsAlgorithm
    override val provider: String = "BC"
    override val jsonWebKey: JsonWebKey

    init {
        val privateKeyString = loadResource(resourceLoader, config.privateKey.toString())
        val privateKeyRead = PEMParser(StringReader(privateKeyString)).readObject()
        privateKey = JcaPEMKeyConverter().getPrivateKey(privateKeyRead as PrivateKeyInfo)
        val publicKeyString = loadResource(resourceLoader, config.publicKey.toString())
        val publicKeyRead = PEMParser(StringReader(publicKeyString)).readObject()
        val publicKey = JcaPEMKeyConverter().getPublicKey(publicKeyRead as SubjectPublicKeyInfo)
        require(publicKey is ECPublicKey) { "expected ECPublicKey" }
        val ecCurve = EcCurve.SECP_256_R_1
        jwsAlgorithm = JwsAlgorithm.ES256
        jsonWebKey = JsonWebKey.fromJcaKey(publicKey, ecCurve)!!
        log.info("Loaded public key: ${publicKey.encoded.encodeBase64()}")
    }

    private fun loadResource(resourceLoader: ResourceLoader, path: String) =
        StreamUtils.copyToString(resourceLoader.getResource(path).inputStream, Charset.defaultCharset())

}


class KeyStoreAdapter(
    config: KeyStoreConfiguration
) : KeyAdapter {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override val privateKey: PrivateKey
    override val jwsAlgorithm: JwsAlgorithm
    override val provider: String = config.provider ?: "BC"
    override val jsonWebKey: JsonWebKey

    init {
        val keyStore = KeyStore.getInstance(config.type, config.provider ?: "BC")
        keyStore.load(config.path.toURL().openStream(), config.password?.toCharArray() ?: charArrayOf())
        privateKey = keyStore.getKey(config.alias, config.aliasPassword?.toCharArray() ?: charArrayOf()) as PrivateKey
        val publicKey = keyStore.getCertificate(config.alias).publicKey
        require(publicKey is ECPublicKey) { "expected ECPublicKey" }
        val ecCurve = EcCurve.SECP_256_R_1
        jwsAlgorithm = JwsAlgorithm.ES256
        jsonWebKey = JsonWebKey.fromJcaKey(publicKey, ecCurve)!!
        log.info("Loaded public key: ${publicKey.encoded.encodeBase64()}")
    }

}

class RandomKeyAdapter : KeyAdapter {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override val privateKey: PrivateKey
    override val jwsAlgorithm: JwsAlgorithm
    override val provider: String = "BC"
    override val jsonWebKey: JsonWebKey

    init {
        val keyPair = KeyPairGenerator.getInstance("EC").also { it.initialize(256) }.generateKeyPair()
        privateKey = keyPair.private
        val ecCurve = EcCurve.SECP_256_R_1
        jwsAlgorithm = JwsAlgorithm.ES256
        jsonWebKey = JsonWebKey.fromJcaKey(keyPair.public as ECPublicKey, ecCurve)!!
        log.info("Generated new key pair with public key: ${keyPair.public.encoded.encodeBase64()}")
    }

}