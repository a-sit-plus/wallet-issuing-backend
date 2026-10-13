package at.asitplus.wallet.backend.config

import at.asitplus.signum.indispensable.pki.X509Certificate as SignumCertificate
import at.asitplus.wallet.lib.agent.KeyStoreMaterial
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Date

/** Real CA hierarchy, so tests exercise the transported chain instead of accepting self-signed stand-ins. */
internal class MetadataTestPki(val identifier: String = "NTRAT-test-issuer") {
    val rootKey = keyPair()
    val root = certificate(rootKey, "CN=Root,O=Test Provider", null, rootKey, true)
    val intermediateKey = keyPair()
    val intermediate = certificate(intermediateKey, "CN=Intermediate,O=Test Provider", root, rootKey, true)
    val leafKey = keyPair()
    val leaf = certificate(leafKey, "CN=Issuer,2.5.4.97=$identifier", intermediate, intermediateKey, false)
    val chain = listOf(leaf, intermediate, root)
    val signumChain get() = chain.map { requireNotNull(SignumCertificate.decodeFromByteArray(it.encoded)) }
    val keyMaterial = material(leafKey, chain)

    fun material(key: KeyPair, certificates: List<X509Certificate>): KeyStoreMaterial {
        val store = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("signer", key.private, charArrayOf(), certificates.toTypedArray())
        }
        return KeyStoreMaterial(store, "signer", charArrayOf(), certAlias = "signer")
    }

    private fun keyPair() = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()

    private fun certificate(
        key: KeyPair, subject: String, issuer: X509Certificate?, issuerKey: KeyPair, ca: Boolean,
    ): X509Certificate {
        val builder = JcaX509v3CertificateBuilder(
            issuer?.let { X500Name.getInstance(it.subjectX500Principal.encoded) } ?: X500Name(subject),
            BigInteger(120, java.security.SecureRandom()),
            Date(System.currentTimeMillis() - 60_000), Date(System.currentTimeMillis() + 86_400_000),
            X500Name(subject), key.public,
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(ca))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(if (ca) KeyUsage.keyCertSign else KeyUsage.digitalSignature))
        if (!ca) builder.addExtension(
            Extension.certificatePolicies, false,
            CertificatePolicies(PolicyInformation(ASN1ObjectIdentifier("0.4.0.194118.1.2"))),
        )
        return JcaX509CertificateConverter().getCertificate(
            builder.build(JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey.private))
        )
    }
}
