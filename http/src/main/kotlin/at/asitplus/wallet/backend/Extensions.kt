package at.asitplus.wallet.backend

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.slf4j.LoggerFactory
import org.springframework.web.util.UriComponentsBuilder

object Extensions {

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun verifyCsr(csrEncoded: ByteArray, expectedSubject: String): PKCS10CertificationRequest? {
        val csr = PKCS10CertificationRequest(csrEncoded)
        val publicKey = BouncyCastleProvider.getPublicKey(csr.subjectPublicKeyInfo)
        if (!csr.isSignatureValid(JcaContentVerifierProviderBuilder().build(publicKey)))
            return null.also { log.warn("CSR signature invalid") }
        if (X500Name(expectedSubject) != csr.subject)
            return null.also { log.warn("CSR subject not correct") }
        return csr
    }

    fun appendPath(url: String, vararg path: String) =
        UriComponentsBuilder.fromHttpUrl(url).pathSegment(*path).toUriString()

}