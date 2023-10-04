package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.service.ChallengeService
import at.asitplus.wallet.backend.service.DeviceBindingStorageService
import io.matthewnelson.component.base64.decodeBase64ToArray
import io.matthewnelson.component.base64.encodeBase64
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArrayOrNull
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import org.springframework.security.authentication.BadCredentialsException
import java.security.cert.CertificateFactory

/**
 * Service to validate the device binding authn response from
 * the Wallet App.
 */
interface DeviceBindingAuthnService {

    fun validate(response: String): DeviceBindingAuthnResult?

}

data class DeviceBindingAuthnResult(
    val bpk: String,
    val certificate: ByteArray,
)

class SimpleDeviceBindingAuthnService(
    private val deviceBindingStorageService: DeviceBindingStorageService,
    private val deviceBindingAuthnChallengeService: ChallengeService,
) : DeviceBindingAuthnService {

    private val certificateFactory = CertificateFactory.getInstance("X.509")

    override fun validate(response: String): DeviceBindingAuthnResult? {
        val jwsObject = try {
            JWSObject.parse(response)
        } catch (e: Throwable) {
            Napier.w("JWS not parsed", e)
            throw BadCredentialsException("jws not parsed", e)
        }
        val decodedCert = jwsObject.header.x509CertChain?.firstOrNull()?.decode()
            ?: throw BadCredentialsException("no x5c")
                .also { Napier.w("No x5c in JWS header") }
        val publicKey = try {
            certificateFactory.generateCertificate(decodedCert.inputStream()).publicKey
        } catch (e: Throwable) {
            throw BadCredentialsException("certificate not parsed")
                .also { Napier.w("Certificate not parsed") }
        }
        if (!jwsObject.verify(DefaultJWSVerifierFactory().createJWSVerifier(jwsObject.header, publicKey)))
            throw BadCredentialsException("signature not valid")
                .also { Napier.w("Signature on JWS not valid") }
        val payloadJsonObject = jwsObject.payload.toJSONObject()
        if (payloadJsonObject?.containsKey("challenge") != true)
            throw BadCredentialsException("challenge not found")
                .also { Napier.w("No challenge in JWS payload") }
        val decodedChallenge = payloadJsonObject["challenge"]?.toString()?.decodeToByteArrayOrNull(Base64())
        if (decodedChallenge == null || !deviceBindingAuthnChallengeService.verifyAndRemove(decodedChallenge))
            throw BadCredentialsException("challenge not valid")
                .also { Napier.w("Challenge in JWS payload not valid") }
        val bpk = deviceBindingStorageService.lookupBpk(decodedCert)
        Napier.i("Translated cert into bpk")
        Napier.v("Translated cert '${decodedCert.encodeToString(Base64())}' into bpk '$bpk'")
        if (bpk == null)
            return null
        return DeviceBindingAuthnResult(bpk, decodedCert)
    }

}