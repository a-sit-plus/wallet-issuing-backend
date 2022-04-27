package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.PkiUtils.verifyCsr
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.CRLReason
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.time.Instant
import java.util.Date
import javax.security.auth.x500.X500Principal
import kotlin.random.Random

class InMemoryPkiService(
    private val certValidityDays: Int = 1,
    private val issuerName: String = "CN=Issuer",
    private val keyAdapter: KeyAdapter = RandomKeyAdapter(),
) : PkiService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val issuer = X500Name(issuerName)
    private val contentSigner = JcaContentSignerBuilder("SHA256withECDSA")
        .setProvider(keyAdapter.provider)
        .build(keyAdapter.privateKey)
    private val crlEntryList = mutableListOf<CrlEntry>()

    override fun verifyAndSign(csrEncoded: ByteArray, expectedSubject: String): ByteArray? {
        try {
            val csr = verifyCsr(csrEncoded, expectedSubject) ?: return null
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
            Date.from(Instant.now().plusSeconds(certValidityDays * 24L * 60L * 60L)),
            issuer,
            subjectPublicKeyInfo
        ).build(contentSigner).encoded

    override fun buildCrl(): ByteArray {
        val crlBuilder = JcaX509v2CRLBuilder(X500Principal(issuerName), Date())
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