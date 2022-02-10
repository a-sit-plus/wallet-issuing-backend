package at.asitplus.wallet.backend.auth

import at.asitplus.wallet.backend.ChallengeService
import at.asitplus.wallet.backend.DeviceBindingStorageService
import at.asitplus.wallet.lib.decodeBase64ToArray
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Component
import java.security.cert.CertificateFactory

/**
 * Converts a [DeviceBindingAuthenticationToken] into a [AuthenticatedDeviceBindingToken] by validating the response to the challenge.
 */
@Component
class DeviceBindingAuthenticationProvider(
    private val deviceBindingStorageService: DeviceBindingStorageService,
    private val deviceBindingAuthnChallengeService: ChallengeService,
) : AuthenticationProvider {

    private val certificateFactory = CertificateFactory.getInstance("X.509")

    override fun authenticate(authentication: Authentication?): Authentication {
        if (authentication !is PreAuthenticatedAuthenticationToken)
            throw BadCredentialsException("not supported")
        val principal = authentication.principal
        if (principal !is DeviceBindingAuthenticationToken)
            throw BadCredentialsException("not supported")
        val jwsObject = try {
            JWSObject.parse(principal.response)
        } catch (e: Throwable) {
            throw BadCredentialsException("jws not parsed", e)
        }
        val decodedCert = jwsObject.header.x509CertChain.firstOrNull()?.decode()
            ?: throw BadCredentialsException("no x5c")
        val publicKey = try {
            certificateFactory.generateCertificate(decodedCert.inputStream()).publicKey
        } catch (e: Throwable) {
            throw BadCredentialsException("certificate not parsed")
        }
        if (!jwsObject.verify(DefaultJWSVerifierFactory().createJWSVerifier(jwsObject.header, publicKey)))
            throw BadCredentialsException("signature not valid")
        val payloadJsonObject = jwsObject.payload.toJSONObject()
        if (!payloadJsonObject.containsKey("challenge"))
            throw BadCredentialsException("challenge not found")
        val decodedChallenge = payloadJsonObject["challenge"].toString().decodeBase64ToArray()
        if (decodedChallenge == null || !deviceBindingAuthnChallengeService.verifyAndRemove(decodedChallenge))
            throw BadCredentialsException("challenge not valued")
        val bpk = deviceBindingStorageService.lookupBpk(decodedCert)
            ?: throw BadCredentialsException("cert not found")
        return AuthenticatedDeviceBindingToken(bpk)
    }

    override fun supports(authentication: Class<*>): Boolean {
        return PreAuthenticatedAuthenticationToken::class.java.isAssignableFrom(authentication)
    }

}