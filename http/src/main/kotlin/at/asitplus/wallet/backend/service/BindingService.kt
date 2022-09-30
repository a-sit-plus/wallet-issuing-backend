package at.asitplus.wallet.backend.service

import at.asitplus.wallet.backend.AttestationService
import at.asitplus.wallet.backend.pki.PkiService
import at.asitplus.wallet.lib.encodeBase16
import at.asitplus.wallet.lib.encodeBase64
import at.asitplus.wallet.lib.jws.JwkType
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

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
)

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
)

class DefaultBindingService(
    private val challengeService: ChallengeService,
    private val pkiService: PkiService,
    private val attestationService: AttestationService,
    private val deviceBindingStorageService: DeviceBindingStorageService,
) : BindingService {

    private val log = LoggerFactory.getLogger(this.javaClass)

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
            return null.also { log.warn("binding challenge invalid: {}", it) }

        val certificate = pkiService.verifyAndSign(csr, buildSubject(challenge))
            ?: return null.also { log.warn("CSR invalid: {}", it) }

        deviceBindingStorageService.store(bpk, certificate.encoded, deviceName, certificate.validUntil)

        val signedPublicKey = attestationService.verifyAttestation(
            attestationCerts,
            certificate.encoded,
            challenge /*already verified by challengeService*/
        )
        log.info("Created new device binding for '{}': {}", bpk, certificate.encoded.encodeBase64())

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
}
