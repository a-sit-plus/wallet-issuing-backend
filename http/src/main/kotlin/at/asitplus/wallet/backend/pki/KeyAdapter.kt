package at.asitplus.wallet.backend.pki

import at.asitplus.crypto.datatypes.X509SignatureAlgorithm
import at.asitplus.wallet.backend.config.KeyFileConfiguration
import at.asitplus.wallet.backend.config.KeyStoreConfiguration
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.springframework.core.io.ResourceLoader
import org.springframework.util.StreamUtils
import java.io.StringReader
import java.math.BigInteger
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.security.*
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.*
import kotlin.random.Random

/**
 * Interface to use different sources of cryptographic keys as [KeyPairAdapter]
 */
interface KeyAdapter {
    val keyPair: KeyPair
    val certificate: at.asitplus.crypto.datatypes.pki.X509Certificate?
    val signingAlgorithm: X509SignatureAlgorithm
}

class KeyFileAdapter(
    config: KeyFileConfiguration,
    resourceLoader: ResourceLoader,
    securityProviderBean: SecurityProviderBean
) : KeyAdapter {

    override val certificate: at.asitplus.crypto.datatypes.pki.X509Certificate?
    override val signingAlgorithm: X509SignatureAlgorithm
    override val keyPair: KeyPair

    init {
        val privateKeyString = loadResource(resourceLoader, config.privateKey.toString())
        val privateKeyRead = PEMParser(StringReader(privateKeyString)).readObject()
        val privateKey = JcaPEMKeyConverter().getPrivateKey(privateKeyRead as PrivateKeyInfo)
        val (jcaKey, jcaCert) = loadCertOrPubKey(config.publicKey, config.certificate, resourceLoader)
        certificate = at.asitplus.crypto.datatypes.pki.X509Certificate.decodeFromByteArray(jcaCert!!.encoded)!!
        this.signingAlgorithm = X509SignatureAlgorithm.ES256
        keyPair = KeyPair(jcaKey, privateKey)
        Napier.i("Loaded public key: '${jcaKey.encoded.encodeToString(Base64())}'")
    }
}


class KeyStoreAdapter(
    provider: Provider,
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

    override val certificate: at.asitplus.crypto.datatypes.pki.X509Certificate?
    override val signingAlgorithm: X509SignatureAlgorithm
    override val keyPair: KeyPair

    init {
        val keyStore = KeyStore.getInstance(type, provider)
        keyStore.load(url.openStream(), password?.toCharArray() ?: charArrayOf())
        val privateKey = keyStore.getKey(alias, aliasPassword?.toCharArray() ?: charArrayOf()) as PrivateKey
        certificate =
            at.asitplus.crypto.datatypes.pki.X509Certificate.decodeFromByteArray(keyStore.getCertificate(alias).encoded)!!
        signingAlgorithm = X509SignatureAlgorithm.ES256
        keyPair = KeyPair(keyStore.getCertificate(alias).publicKey, privateKey)
        Napier.i("Loaded public key from cert: '${certificate.publicKey.encodeToDer().encodeToString(Base64())}'")
    }

}

class RandomKeyAdapter : KeyAdapter {

    override val certificate: at.asitplus.crypto.datatypes.pki.X509Certificate?
    override val signingAlgorithm: X509SignatureAlgorithm
    private val provider: Provider = BouncyCastleProvider().also { Security.addProvider(it) }
    override val keyPair: KeyPair

    init {
        keyPair = KeyPairGenerator.getInstance("EC", provider).also { it.initialize(256) }
            .generateKeyPair()
        signingAlgorithm = X509SignatureAlgorithm.ES256
        val issuer = X500Name("CN=Issuer")
        val contentSigner by lazy { JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private) }
        val builder = X509v3CertificateBuilder(
            /* issuer = */ issuer,
            /* serial = */ BigInteger.valueOf(Random.nextLong()),
            /* notBefore = */ Date(),
            /* notAfter = */ Date.from(Instant.now().plusSeconds(60 * 60 * 24 * 365)),
            /* subject = */ issuer,
            /* publicKeyInfo = */ SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(keyPair.public.encoded))
        )
        certificate =
            at.asitplus.crypto.datatypes.pki.X509Certificate.decodeFromByteArray(builder.build(contentSigner).encoded)!!
        Napier.i("Generated new key pair with public key: '${keyPair.public.encoded.encodeToString(Base64())}'")
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
