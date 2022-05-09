package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.Extensions.daysToSeconds
import at.asitplus.wallet.backend.PkiUtils.verifyCsr
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.CRLReason
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.time.Instant
import java.util.Date
import javax.security.auth.x500.X500Principal
import kotlin.random.Random

class InMemoryPkiService(
    private val certValidityDays: Int = 1,
    private val issuerName: String = "CN=Issuer",
    private val cryptoService: CryptoServiceAdapter = DefaultCryptoServiceAdapter(RandomKeyAdapter()),
) : PkiService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val issuer = X500Name(issuerName)
    private val crlEntryList = mutableListOf<CrlEntry>()
    private val caCertificate = signCertificate(issuer, cryptoService.subjectPublicKeyInfo).encoded

    override fun verifyAndSign(csrEncoded: ByteArray, expectedSubject: String): SignedCertificate? {
        try {
            val csr = verifyCsr(csrEncoded, expectedSubject) ?: return null
            val holder = signCertificate(csr.subject, csr.subjectPublicKeyInfo)
            return SignedCertificate(holder.encoded, holder.notAfter.toInstant())
        } catch (e: Throwable) {
            log.warn("verifyAndSign: error", e)
            return null
        }
    }

    private fun signCertificate(subject: X500Name, subjectPublicKeyInfo: SubjectPublicKeyInfo): X509CertificateHolder =
        X509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(Random.nextLong()),
            Date(),
            Date.from(Instant.now().plusSeconds(certValidityDays.daysToSeconds)),
            issuer,
            subjectPublicKeyInfo
        ).build(cryptoService.jcaContentSigner)

    override fun getCaCertificate(): ByteArray {
        return caCertificate
    }

    override fun getCrl(): ByteArray {
        val crlBuilder = JcaX509v2CRLBuilder(X500Principal(issuerName), Date())
        crlEntryList.forEach {
            crlBuilder.addCRLEntry(it.serialNumber, it.date, CRLReason.unspecified)
        }
        return crlBuilder.build(cryptoService.jcaContentSigner).encoded
    }

    override fun revokeCertificate(certificate: ByteArray) {
        crlEntryList += CrlEntry(X509CertificateHolder(certificate).serialNumber, Date())
    }

    data class CrlEntry(val serialNumber: BigInteger, val date: Date)
}