package at.asitplus.wallet.backend.pki

import at.asitplus.wallet.backend.pki.PkiUtils.verifyCsr
import at.asitplus.wallet.backend.service.CryptoServiceAdapter
import at.asitplus.wallet.backend.service.DefaultCryptoServiceAdapter
import at.asitplus.wallet.backend.service.SecureRandom
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.CRLReason
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder
import java.math.BigInteger
import java.util.Date
import javax.security.auth.x500.X500Principal
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Signs certificates for development deployments,
 * i.e. with the key from a [CryptoServiceAdapter].
 */
class InMemoryPkiService(
    private val certValidity: Duration = 1.days,
    private val issuerName: String = "CN=Issuer",
    private val cryptoService: CryptoServiceAdapter = DefaultCryptoServiceAdapter(RandomKeyAdapter()),
) : PkiService {


    private val crlEntryList = mutableListOf<CrlEntry>()
    private val issuer = cryptoService.certificate?.let {
        X500Name.getInstance(BCStyle.INSTANCE, it.subjectX500Principal.encoded)
    } ?: X500Name(issuerName)

    private val caCertificate =
        cryptoService.certificate ?: JcaX509CertificateConverter().getCertificate(
            signCertificate(
                issuer,
                cryptoService.subjectPublicKeyInfo
            )
        )

    override fun verifyAndSign(csrEncoded: ByteArray, expectedSubject: String): SignedCertificate? =
        kotlin.runCatching {
            val csr = verifyCsr(csrEncoded, expectedSubject) ?: return null
            val holder = signCertificate(csr.subject, csr.subjectPublicKeyInfo)
            SignedCertificate(holder.encoded, holder.notAfter.toInstant().toKotlinInstant())
        }.getOrElse { e ->
            Napier.e("verifyAndSign: error", e)
            null
        }

    private fun signCertificate(
        subject: X500Name,
        subjectPublicKeyInfo: SubjectPublicKeyInfo
    ): X509CertificateHolder =
        X509v3CertificateBuilder(
            /* issuer = */ issuer,
            /* serial = */ BigInteger.valueOf(SecureRandom.nextLong()),
            /* notBefore = */ Clock.System.now().toJavaDate(),
            /* notAfter = */ (Clock.System.now() + certValidity).toJavaDate(),
            /* subject = */ subject,
            /* publicKeyInfo = */ subjectPublicKeyInfo
        ).build(cryptoService.jcaContentSigner)

    override fun getCaCertificate(): ByteArray {
        return caCertificate.encoded
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


private fun kotlinx.datetime.Instant.toJavaDate(): Date = Date.from(toJavaInstant())