package at.asitplus.wallet.backend.pki

import io.github.aakira.napier.Napier
import kotlinx.datetime.Instant
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.slf4j.LoggerFactory

interface PkiService {

    /**
     * Verifies the Certification Request (PKCS#10) of the client,
     * and creates a signed certificate for that public key.
     */
    fun verifyAndSign(csrEncoded: ByteArray, expectedSubject: String): SignedCertificate?

    /**
     * Builds (or loads remotely) an X.509 Certificate Revocation List
     */
    fun getCrl(): ByteArray?

    /**
     * Gets the X.509 Certificate for the key pair that signs device binding certificates
     */
    fun getCaCertificate(): ByteArray?

    /**
     * Marks the certificate as revoked, i.e. it will be added to [getCrl]
     */
    fun revokeCertificate(certificate: ByteArray)

}

object PkiUtils {


    fun verifyCsr(csrEncoded: ByteArray, expectedSubject: String): PKCS10CertificationRequest? {
        val csr = PKCS10CertificationRequest(csrEncoded)
        val publicKey = BouncyCastleProvider.getPublicKey(csr.subjectPublicKeyInfo)
        if (!csr.isSignatureValid(JcaContentVerifierProviderBuilder().build(publicKey)))
            return null.also { Napier.w("CSR signature invalid") }
        if (X500Name(expectedSubject) != csr.subject)
            return null.also { Napier.w("CSR subject not correct") }
        return csr
    }

}

data class SignedCertificate(
    val encoded: ByteArray,
    val validUntil: Instant,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignedCertificate

        if (!encoded.contentEquals(other.encoded)) return false
        if (validUntil != other.validUntil) return false

        return true
    }

    override fun hashCode(): Int {
        var result = encoded.contentHashCode()
        result = 31 * result + validUntil.hashCode()
        return result
    }
}