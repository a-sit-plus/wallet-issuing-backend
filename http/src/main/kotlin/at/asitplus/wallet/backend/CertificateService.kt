package at.asitplus.wallet.backend

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.time.Instant
import java.util.Date
import kotlin.random.Random

interface CertificateService {

    fun verifyAndSign(csrEncoded: ByteArray): ByteArray?

}

class InMemoryCertificateService(
    private val lifetimeSeconds: Long = 60
) : CertificateService {

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC").generateKeyPair()!!
    private val issuer = X500Name("CN=Issuer")
    private val contentSigner = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)

    override fun verifyAndSign(csrEncoded: ByteArray): ByteArray? {
        try {
            val csr = PKCS10CertificationRequest(csrEncoded)
            val publicKey = BouncyCastleProvider.getPublicKey(csr.subjectPublicKeyInfo)
            if (!csr.isSignatureValid(JcaContentVerifierProviderBuilder().build(publicKey))) {
                log.warn("verifyAndSign: CSR signature invalid")
                return null
            }
            val x500Name = issuer
            return X509v3CertificateBuilder(
                csr.subject,
                BigInteger.valueOf(Random.nextLong()),
                Date(),
                Date.from(Instant.now().plusSeconds(lifetimeSeconds)),
                x500Name,
                csr.subjectPublicKeyInfo
            ).build(contentSigner).encoded
        } catch (e: Throwable) {
            log.warn("verifyAndSign: error", e)
            return null
        }
    }

}