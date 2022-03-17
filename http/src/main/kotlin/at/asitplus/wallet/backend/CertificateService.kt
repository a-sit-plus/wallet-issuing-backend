package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.encodeBase64
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDSASigner
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
import java.security.interfaces.ECPrivateKey
import java.time.Instant
import java.util.Date
import kotlin.random.Random

interface CertificateService {

    /**
     * Verifies the Certification Request (PKCS#10) of the client,
     * and creates a signed certificate for that public key.
     */
    fun verifyAndSign(csrEncoded: ByteArray, expectedSubject: String): ByteArray?

    /**
     * Verifies the Android Key Attestation or Apple App Attestation
     * structures of the client, creating a signed public key
     * if the device can be verified.
     */
    fun verifyAttestation(attestationCerts: List<ByteArray>): String?

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

    override fun verifyAndSign(csrEncoded: ByteArray, expectedSubject: String): ByteArray? {
        try {
            val csr = PKCS10CertificationRequest(csrEncoded)
            val publicKey = BouncyCastleProvider.getPublicKey(csr.subjectPublicKeyInfo)
            if (!csr.isSignatureValid(JcaContentVerifierProviderBuilder().build(publicKey))) {
                log.warn("verifyAndSign: CSR signature invalid")
                return null
            }
            if (csr.subject.toString() != expectedSubject) { // todo improve check, see E-ID pentest
                log.warn("verifyAndSign: Subject not correct")
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

    override fun verifyAttestation(attestationCerts: List<ByteArray>): String? {
        try {
            // TODO Verify attestation, get code from E-ID Binding Service?
            val publicKey =
                keyPair.public // TODO read from attestationCert ... if it is contained in the Apple structure
            return JWSObject(
                JWSHeader(JWSAlgorithm.ES256),
                Payload(mapOf("pk" to publicKey.encoded.encodeBase64()))
            ).also {
                it.sign(ECDSASigner(keyPair.private as ECPrivateKey))
            }.serialize()
        } catch (e: Throwable) {
            log.warn("verifyAttestation: error", e)
            return null
        }
    }
}