package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.encodeBase64
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.util.Base64
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.interfaces.ECPrivateKey
import java.time.Instant
import java.util.Date
import kotlin.random.Random

@Service
class Client {

    final lateinit var keyPair: KeyPair

    constructor(keyPair: KeyPair) {
        Security.addProvider(BouncyCastleProvider())
        this.keyPair = keyPair
    }

    constructor() {
        Security.addProvider(BouncyCastleProvider())
        this.keyPair = KeyPairGenerator.getInstance("EC").generateKeyPair()!!
    }

    private val lifetimeSeconds: Long = 60
    private val issuer = X500Name("CN=Issuer")
    private val contentSigner by lazy { JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private) }

    final val selfSignedCert: X509CertificateHolder by lazy {
        X509v3CertificateBuilder(
            issuer,
            BigInteger.valueOf(Random.nextLong()),
            Date(),
            Date.from(Instant.now().plusSeconds(lifetimeSeconds)),
            issuer,
            SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(keyPair.public.encoded))
        ).build(contentSigner)!!
    }

    fun generateCsr(subject: String): ByteArray {
        return JcaPKCS10CertificationRequestBuilder(X500Name(subject), keyPair.public).build(
            JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
        ).encoded
    }

    fun answerBindingChallenge(challenge: ByteArray): String = answerBindingChallenge(challenge, selfSignedCert.encoded)

    fun answerBindingChallenge(challenge: ByteArray, certificate: ByteArray): String = signBindingChallenge(
        JWSObject(
            JWSHeader.Builder(JWSAlgorithm.ES256).x509CertChain(listOf(Base64.encode(certificate))).build(),
            Payload(mapOf("challenge" to challenge.encodeBase64()))
        )
    )

    fun signBindingChallenge(jws: JWSObject) =
        jws.also { it.sign(ECDSASigner(keyPair.private as ECPrivateKey)) }.serialize()

}