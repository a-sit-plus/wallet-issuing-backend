package at.asitplus.wallet.backend

import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.time.Instant
import java.util.Date
import kotlin.random.Random

@Service
class ClientCertificateService(
    private val lifetimeSeconds: Long = 60
) {

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    final val keyPair: KeyPair = KeyPairGenerator.getInstance("EC").generateKeyPair()!!
    private val issuer = X500Name("CN=Issuer")
    private val contentSigner = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
    final val cert: X509CertificateHolder = X509v3CertificateBuilder(
        issuer,
        BigInteger.valueOf(Random.nextLong()),
        Date(),
        Date.from(Instant.now().plusSeconds(lifetimeSeconds)),
        issuer,
        SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(keyPair.public.encoded))
    ).build(contentSigner)!!

}