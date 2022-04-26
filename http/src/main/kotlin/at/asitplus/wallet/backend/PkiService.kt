package at.asitplus.wallet.backend

import com.nimbusds.jose.JWSObject

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
