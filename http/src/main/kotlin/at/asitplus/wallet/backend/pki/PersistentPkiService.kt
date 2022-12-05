package at.asitplus.wallet.backend.pki

import at.asitplus.wallet.backend.service.CryptoServiceAdapter
import at.asitplus.wallet.backend.service.DefaultCryptoServiceAdapter
import at.asitplus.wallet.backend.data.IssuedCertificate
import at.asitplus.wallet.backend.data.IssuedCertificateRepository
import at.asitplus.wallet.backend.pki.PkiUtils.verifyCsr
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
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder
import java.math.BigInteger
import java.util.*
import kotlin.time.Duration
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

/**
 * Signs certificates with a remote signing service,
 * storing all issued certificates in [IssuedCertificate].
 */
class PersistentPkiService(
    private val certValidity: Duration = 30.days,
    private val issuedCertificateRepository: IssuedCertificateRepository,
    private val cryptoService: CryptoServiceAdapter = DefaultCryptoServiceAdapter(RandomKeyAdapter()),
    private val clock: Clock
) : PkiService {


    private val caCertificate =
        cryptoService.certificate ?: throw RuntimeException("No certificate provided")

    private val issuer = X500Name.getInstance(BCStyle.INSTANCE, caCertificate.subjectX500Principal.encoded)

    override fun verifyAndSign(csrEncoded: ByteArray, expectedSubject: String): SignedCertificate? {
        try {
            val csr = verifyCsr(csrEncoded, expectedSubject) ?: return null
            val holder = signCertificate(csr.subject, csr.subjectPublicKeyInfo)
            return SignedCertificate(holder.encoded, holder.notAfter.toInstant().toKotlinInstant())
        } catch (e: Throwable) {
            Napier.e("verifyAndSign: error", e) // TODO I think bouncycastle exceptions are fine?
            return null
        }
    }

    private fun signCertificate(
        subject: X500Name,
        subjectPublicKeyInfo: SubjectPublicKeyInfo
    ): X509CertificateHolder {
        val validFrom = clock.now()
        val validUntil = clock.now() + certValidity
        val serialNumber = uniqueSerialNumber()
        return X509v3CertificateBuilder(
            /* issuer = */ issuer,
            /* serial = */ BigInteger.valueOf(serialNumber),
            /* notBefore = */ Date.from(validFrom.toJavaInstant()),
            /* notAfter = */ Date.from(validUntil.toJavaInstant()),
            /* subject = */ subject,
            /* publicKeyInfo = */ subjectPublicKeyInfo
        ).build(cryptoService.jcaContentSigner).also {
            val issuedCertificate = IssuedCertificate(
                subject = subject.toString(),
                issuer = issuer.toString(),
                validFrom = validFrom.toJavaInstant(),
                validUntil = validUntil.toJavaInstant(),
                serialNumber = serialNumber,
                certificate = it.encoded
            )
            issuedCertificateRepository.save(issuedCertificate)
        }
    }

    private fun uniqueSerialNumber(): Long {
        val serialNumber = Random.nextLong()
        if (issuedCertificateRepository.findBySerialNumber(serialNumber) != null)
            return uniqueSerialNumber()
        return serialNumber
    }

    override fun getCaCertificate(): ByteArray {
        return caCertificate.encoded
    }

    override fun getCrl(): ByteArray {
        val crlBuilder = JcaX509v2CRLBuilder(caCertificate.subjectX500Principal, Date())
        issuedCertificateRepository.findAllByRevokedTrueAndValidFromBeforeAndValidUntilAfter(
            clock.now().toJavaInstant(),
            clock.now().toJavaInstant()
        ).forEach {
            crlBuilder.addCRLEntry(
                /* userCertificateSerial = */ BigInteger.valueOf(it.serialNumber),
                /* revocationDate = */ Date.from(it.revocationDate ?: clock.now().toJavaInstant()),
                /* reason = */ CRLReason.unspecified
            )
        }
        return crlBuilder.build(cryptoService.jcaContentSigner).encoded
    }

    override fun revokeCertificate(certificate: ByteArray) {
        issuedCertificateRepository.findByCertificate(certificate)?.let {
            it.revocationDate = clock.now().toJavaInstant()
            it.revoked = true
            issuedCertificateRepository.save(it)
        }
    }

}