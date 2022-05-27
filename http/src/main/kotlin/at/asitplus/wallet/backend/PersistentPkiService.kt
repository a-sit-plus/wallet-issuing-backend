package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.Extensions.InstantNowPlusDays
import at.asitplus.wallet.backend.PkiUtils.verifyCsr
import at.asitplus.wallet.backend.data.IssuedCertificate
import at.asitplus.wallet.backend.data.IssuedCertificateRepository
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

/**
 * Signs certificates with a remote signing service,
 * storing all issued certificates in [IssuedCertificate].
 */
class PersistentPkiService(
    private val certValidityDays: Int = 30,
    private val issuerName: String = "CN=Persistent-Issuer",
    private val issuedCertificateRepository: IssuedCertificateRepository,
    // TODO Use RemoteKey
    private val cryptoService: CryptoServiceAdapter = DefaultCryptoServiceAdapter(RandomKeyAdapter()),
) : PkiService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val issuer = X500Name(issuerName)

    // TODO Load from remote, and cache here
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

    private fun signCertificate(subject: X500Name, subjectPublicKeyInfo: SubjectPublicKeyInfo): X509CertificateHolder {
        val validFrom = Instant.now()
        val validUntil = InstantNowPlusDays(certValidityDays)
        val serialNumber = uniqueSerialNumber()
        return X509v3CertificateBuilder(
            /* issuer = */ issuer,
            /* serial = */ BigInteger.valueOf(serialNumber),
            /* notBefore = */ Date.from(validFrom),
            /* notAfter = */ Date.from(validUntil),
            /* subject = */ subject,
            /* publicKeyInfo = */ subjectPublicKeyInfo
        ).build(cryptoService.jcaContentSigner).also {
            val issuedCertificate = IssuedCertificate(
                subject = subject.toString(),
                issuer = issuerName,
                validFrom = validFrom,
                validUntil = validUntil,
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
        return caCertificate
    }

    override fun getCrl(): ByteArray {
        val crlBuilder = JcaX509v2CRLBuilder(X500Principal(issuerName), Date())
        issuedCertificateRepository.findAllByRevokedTrueAndValidFromBeforeAndValidUntilAfter(
            Instant.now(),
            Instant.now()
        ).forEach {
            crlBuilder.addCRLEntry(
                /* userCertificateSerial = */ BigInteger.valueOf(it.serialNumber),
                /* revocationDate = */ Date.from(it.revocationDate ?: Instant.now()),
                /* reason = */ CRLReason.unspecified
            )
        }
        return crlBuilder.build(cryptoService.jcaContentSigner).encoded
    }

    override fun revokeCertificate(certificate: ByteArray) {
        issuedCertificateRepository.findByCertificate(certificate)?.let {
            it.revocationDate = Instant.now()
            it.revoked = true
            issuedCertificateRepository.save(it)
        }
    }

}