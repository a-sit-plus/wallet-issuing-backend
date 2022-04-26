package at.asitplus.wallet.backend

import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.ECDSASigner
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.CRLReason
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder
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
import javax.security.auth.x500.X500Principal
import kotlin.random.Random

interface PkiService {

    /**
     * Verifies the Certification Request (PKCS#10) of the client,
     * and creates a signed certificate for that public key.
     */
    fun verifyAndSign(csrEncoded: ByteArray, expectedSubject: String): ByteArray?

    /**
     * Builds an X.509 Certificate Revocation List
     */
    fun buildCrl(): ByteArray

    /**
     * Marks the certificate as revoked, i.e. it will be added to [buildCrl]
     */
    fun revokeCertificate(certificate: ByteArray)

    fun signAttestedPublicKey(it: JWSObject)

}

class InMemoryPkiService(
    // TODO Longer per default!
    private val lifetimeSeconds: Long = 60
) : PkiService {

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC").generateKeyPair()!!
    private val issuer = X500Name("CN=Issuer")
    private val contentSigner = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
    private val crlEntryList = mutableListOf<CrlEntry>()

    override fun verifyAndSign(csrEncoded: ByteArray, expectedSubject: String): ByteArray? {
        try {
            val csr = PKCS10CertificationRequest(csrEncoded)
            val publicKey = BouncyCastleProvider.getPublicKey(csr.subjectPublicKeyInfo)
            if (!csr.isSignatureValid(JcaContentVerifierProviderBuilder().build(publicKey))) {
                log.warn("verifyAndSign: CSR signature invalid")
                return null
            }
            if (X500Name(expectedSubject) != csr.subject) {
                log.warn("verifyAndSign: Subject not correct")
                return null
            }
            return signCertificate(csr.subject, csr.subjectPublicKeyInfo)
        } catch (e: Throwable) {
            log.warn("verifyAndSign: error", e)
            return null
        }
    }

    private fun signCertificate(subject: X500Name, subjectPublicKeyInfo: SubjectPublicKeyInfo): ByteArray =
        X509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(Random.nextLong()),
            Date(),
            Date.from(Instant.now().plusSeconds(lifetimeSeconds)),
            issuer,
            subjectPublicKeyInfo
        ).build(contentSigner).encoded

    override fun signAttestedPublicKey(it: JWSObject) {
        it.sign(ECDSASigner(keyPair.private as ECPrivateKey))
    }

    override fun buildCrl(): ByteArray {
        val crlBuilder = JcaX509v2CRLBuilder(X500Principal("CN=Issuer"), Date())
        crlEntryList.forEach {
            crlBuilder.addCRLEntry(it.serialNumber, it.date, CRLReason.unspecified)
        }
        return crlBuilder.build(contentSigner).encoded
    }

    override fun revokeCertificate(certificate: ByteArray) {
        crlEntryList += CrlEntry(X509CertificateHolder(certificate).serialNumber, Date())
    }

    data class CrlEntry(val serialNumber: BigInteger, val date: Date)
}
