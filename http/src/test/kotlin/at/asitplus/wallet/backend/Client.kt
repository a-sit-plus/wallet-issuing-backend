package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.service.fromJcaKey
import at.asitplus.wallet.lib.jws.EcCurve
import at.asitplus.wallet.lib.jws.JsonWebKey
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.util.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.properties.Delegates
import kotlin.random.Random

class Client {

    lateinit var keyPair: KeyPair
    var keyId: String
    var lifetimeSeconds: Long by Delegates.notNull()

    constructor(keyPair: KeyPair) {
        this.keyPair = keyPair
        this.keyId = JsonWebKey.fromJcaKey(keyPair.public as ECPublicKey, EcCurve.SECP_256_R_1)!!.keyId!!
        this.lifetimeSeconds = 60
    }

    constructor(lifetimeSeconds: Long = 60) {
        this.keyPair = KeyPairGenerator.getInstance("EC").generateKeyPair()!!
        this.keyId = JsonWebKey.fromJcaKey(keyPair.public as ECPublicKey, EcCurve.SECP_256_R_1)!!.keyId!!
        this.lifetimeSeconds = lifetimeSeconds
    }

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
            Payload(mapOf("challenge" to challenge.encodeToString(io.matthewnelson.encoding.base64.Base64())))
        )
    )

    fun signBindingChallenge(jws: JWSObject) =
        jws.also { it.sign(ECDSASigner(keyPair.private as ECPrivateKey)) }.serialize()

}