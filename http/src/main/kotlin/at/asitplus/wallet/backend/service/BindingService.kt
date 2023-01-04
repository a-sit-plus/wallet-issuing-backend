package at.asitplus.wallet.backend.service

import at.asitplus.attestation.AttestationService
import at.asitplus.wallet.backend.pki.PkiService
import at.asitplus.wallet.lib.jws.EcCurve
import at.asitplus.wallet.lib.jws.JsonWebKey
import at.asitplus.wallet.lib.jws.JwkType
import at.asitplus.wallet.pupilid.AttestedPublicKey
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import io.github.aakira.napier.Napier
import io.matthewnelson.component.base64.encodeBase64
import io.matthewnelson.component.encoding.base16.encodeBase16
import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.security.interfaces.ECPublicKey

interface BindingService {

    /**
     * Issues new binding parameters.
     */
    fun getBindingParams(deviceName: String): BindingParams

    /**
     * Signs a new binding certificate for the client's [csr].
     */
    fun signCertificate(
        csr: ByteArray,
        challenge: ByteArray,
        deviceName: String,
        attestationCerts: List<ByteArray>,
        bpk: String
    ): BindingCertificate?

    /**
     * Confirms the binding process.
     */
    fun confirm(success: Boolean): Boolean?
}

/**
 * Parameters sent to clients to create a new device binding.
 */
data class BindingParams(
    val challenge: ByteArray,
    val subject: String,
    val keyType: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BindingParams

        if (!challenge.contentEquals(other.challenge)) return false
        if (subject != other.subject) return false
        if (keyType != other.keyType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = challenge.contentHashCode()
        result = 31 * result + subject.hashCode()
        result = 31 * result + keyType.hashCode()
        return result
    }
}

/**
 * Core result of the device binding process.
 */
data class BindingCertificate(
    /**
     * Signed device binding certificate
     */
    val certificate: ByteArray,
    /**
     * Serialized [AttestedPublicKey].
     */
    val attestedPublicKey: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BindingCertificate

        if (!certificate.contentEquals(other.certificate)) return false
        if (attestedPublicKey != other.attestedPublicKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = certificate.contentHashCode()
        result = 31 * result + (attestedPublicKey?.hashCode() ?: 0)
        return result
    }
}

class DefaultBindingService(
    private val challengeService: ChallengeService,
    private val pkiService: PkiService,
    private val cryptoService: CryptoServiceAdapter,
    private val attestationService: AttestationService,
    private val deviceBindingStorageService: DeviceBindingStorageService,
) : BindingService {


    /**
     * Issues new binding parameters.
     */
    override fun getBindingParams(deviceName: String): BindingParams {
        val challenge = runBlocking { challengeService.generate() }
        val subject = buildSubject(challenge)
        val keyType = JwkType.EC.text
        return BindingParams(challenge, subject, keyType)
    }

    /**
     * Signs a new binding certificate for the client's [csr].
     */
    override fun signCertificate(
        csr: ByteArray,
        challenge: ByteArray,
        deviceName: String,
        attestationCerts: List<ByteArray>,
        bpk: String
    ): BindingCertificate? {
        if (!challengeService.verifyAndRemove(challenge))
            return null.also { Napier.w("binding challenge null") }

        val bindingPublicKey =
            kotlin.runCatching { BouncyCastleProvider.getPublicKey(PKCS10CertificationRequest(csr).subjectPublicKeyInfo) }
                .getOrElse { error ->
                    // TODO: Cursory look over thrown exceptions shows that it may leak some stuff, but only if data
                    // is malformed anyways. Is this fine?
                    return null.also {
                        Napier.w("Could not parse public key from CSR", error)
                        Napier.v("Error was: ", error)
                    }
                } as ECPublicKey


        val attestedPublicKey = attestationService.verifyKeyAttestation(
            attestationCerts,
            challenge, /*already verified by challengeService*/
            bindingPublicKey,
        ).fold({ err ->
            null.also { Napier.v(throwable = err.cause) { "Attestation error: ${err.explanation}" } }
        }) { publicKey, details ->
            Napier.d { "Attestation successful. details:" }
            publicKey
        } ?: return null.also { Napier.w("Attestation failed! Could not verify device integrity") }


        val certificate = pkiService.verifyAndSign(csr, buildSubject(challenge))
            ?: return null.also { Napier.w("CSR invalid") }

        val signedPublicKey = cryptoService.wrapInJws(attestedPublicKey)

        deviceBindingStorageService.store(bpk, certificate.encoded, deviceName, certificate.validUntil)

        Napier.i("Created new device binding")
        Napier.v("bpk: $bpk, binding certificate: ${certificate.encoded.encodeBase64()}")

        return BindingCertificate(certificate.encoded, signedPublicKey)
    }

    private fun buildSubject(challenge: ByteArray) = "CN=${challenge.encodeBase16()}"

    /**
     * Confirms the binding process.
     */
    override fun confirm(success: Boolean): Boolean? {
        if (!success)
            return null
        return true
    }

    private fun CryptoServiceAdapter.wrapInJws(pubKey: ECPublicKey): String {
        val publicKey = JsonWebKey.fromJcaKey(pubKey, EcCurve.SECP_256_R_1)!!
        val attestedPublicKey = AttestedPublicKey(publicKey.keyId!!)
        return JWSObject(
            JWSHeader(jwsAlgorithm.joseType),
            Payload(attestedPublicKey.serialize().encodeToByteArray())
        ).also {
            it.sign(jwsContentSigner)
        }.serialize()
    }


}
