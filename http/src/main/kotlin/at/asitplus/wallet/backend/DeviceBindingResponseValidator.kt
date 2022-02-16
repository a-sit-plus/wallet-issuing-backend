package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.decodeBase64ToArray
import at.asitplus.wallet.lib.encodeBase64
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.BadCredentialsException
import java.security.cert.CertificateFactory

interface DeviceBindingResponseValidator {

    fun validate(response: String): DeviceBindingValidatorResult?

}

data class DeviceBindingValidatorResult(
    val bpk: String,
    val certificate: ByteArray,
)

class SimpleDeviceBindingResponseValidator(
    private val deviceBindingStorageService: DeviceBindingStorageService,
    private val deviceBindingAuthnChallengeService: ChallengeService,
) : DeviceBindingResponseValidator {

    private val log = LoggerFactory.getLogger(this.javaClass)
    private val certificateFactory = CertificateFactory.getInstance("X.509")

    override fun validate(response: String): DeviceBindingValidatorResult? {
        val jwsObject = try {
            JWSObject.parse(response)
        } catch (e: Throwable) {
            log.warn("JWS not parsed", e)
            throw BadCredentialsException("jws not parsed", e)
        }
        val decodedCert = jwsObject.header.x509CertChain.firstOrNull()?.decode()
            ?: throw BadCredentialsException("no x5c")
                .also { log.warn("No x5c in JWS header") }
        val publicKey = try {
            certificateFactory.generateCertificate(decodedCert.inputStream()).publicKey
        } catch (e: Throwable) {
            throw BadCredentialsException("certificate not parsed")
                .also { log.warn("Certificate not parsed") }
        }
        if (!jwsObject.verify(DefaultJWSVerifierFactory().createJWSVerifier(jwsObject.header, publicKey)))
            throw BadCredentialsException("signature not valid")
                .also { log.warn("Signature on JWS not valid") }
        val payloadJsonObject = jwsObject.payload.toJSONObject()
        if (!payloadJsonObject.containsKey("challenge"))
            throw BadCredentialsException("challenge not found")
                .also { log.warn("No challenge in JWS payload") }
        val decodedChallenge = payloadJsonObject["challenge"].toString().decodeBase64ToArray()
        if (decodedChallenge == null || !deviceBindingAuthnChallengeService.verifyAndRemove(decodedChallenge))
            throw BadCredentialsException("challenge not valid")
                .also { log.warn("Challenge in JWS payload not valid") }
        val bpk = deviceBindingStorageService.lookupBpk(decodedCert)
        log.debug("Translated cert '{}' into bpk '{}'", decodedCert.encodeBase64(), bpk)
        if (bpk == null)
            return null
        return DeviceBindingValidatorResult(bpk, decodedCert)
    }

}