package at.asitplus.wallet.backend

import at.asitplus.attestation.android.AndroidAttestationChecker
import at.asitplus.attestation.android.AndroidAttestationConfiguration
import at.asitplus.wallet.backend.config.IOSAttestationConfigurationProperties
import at.asitplus.wallet.backend.service.CryptoServiceAdapter
import at.asitplus.wallet.backend.service.fromJcaKey
import at.asitplus.wallet.backend.service.joseType
import at.asitplus.wallet.lib.decodeBase64ToArray
import at.asitplus.wallet.lib.encodeBase64
import at.asitplus.wallet.lib.jws.EcCurve
import at.asitplus.wallet.lib.jws.JsonWebKey
import at.asitplus.wallet.lib.toJavaClock
import at.asitplus.wallet.lib.toJavaDate
import at.asitplus.wallet.pupilid.AttestedPublicKey
import ch.veehait.devicecheck.appattest.AppleAppAttest
import ch.veehait.devicecheck.appattest.assertion.Assertion
import ch.veehait.devicecheck.appattest.assertion.AssertionChallengeValidator
import ch.veehait.devicecheck.appattest.attestation.ValidatedAttestation
import ch.veehait.devicecheck.appattest.common.App
import ch.veehait.devicecheck.appattest.common.AppleAppAttestEnvironment
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import kotlinx.datetime.Clock
import net.swiftzer.semver.SemVer
import org.slf4j.LoggerFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.util.*

interface AttestationService {

    /**
     * Verifies the Android Key Attestation or Apple App Attestation
     * structures of the client (in [attestationCerts]),
     * creating a signed public key (with data from [bindingCertificate])
     * if the device can be verified and [challenge] matches the attestation challenge.
     */
    fun verifyAttestation(
        attestationCerts: List<ByteArray>,
        bindingCertificate: ByteArray,
        challenge: ByteArray
    ): String?

}

class DefaultAttestationService(
    private val cryptoService: CryptoServiceAdapter,
    androidAttestationConfiguration: AndroidAttestationConfiguration,
    private val iosCfg: IOSAttestationConfigurationProperties,
    private val clock: Clock
) : AttestationService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val android =
        AndroidAttestationChecker(androidAttestationConfiguration) { expected, actual -> expected contentEquals actual }

    private val appleAppAttest = AppleAppAttest(
        app = App(iosCfg.teamIdentifier, iosCfg.bundleIdentifier),
        appleAppAttestEnvironment = if (iosCfg.devStage) AppleAppAttestEnvironment.DEVELOPMENT else AppleAppAttestEnvironment.PRODUCTION,
    )


    private val iosNoopChallengeValidator = object : AssertionChallengeValidator {
        override fun validate(
            assertionObj: Assertion,
            clientData: ByteArray,
            attestationPublicKey: ECPublicKey,
            challenge: ByteArray,
        ): Boolean = TODO("Your application specific challenge validation routine")
    }

    private val attestationValidator = appleAppAttest.createAttestationValidator(clock = clock.toJavaClock())


    /**
     * Verifies the Android Key Attestation or Apple App Attestation
     * structures of the client (in [attestationCerts]),
     * creating a signed public key (with data from [bindingCertificate])
     * if the device can be verified.
     */
    override fun verifyAttestation(
        attestationCerts: List<ByteArray>,
        bindingCertificate: ByteArray,
        challenge: ByteArray
    ): String? {
        try {
            val certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(bindingCertificate.inputStream()) as X509Certificate
            if (!verifyAttestationClient(attestationCerts, certificate, challenge))
                return null.also {
                    log.error("Could not verify attestation chain: {}", attestationCerts.map { it.encodeBase64() })
                }
            val publicKey = JsonWebKey.fromJcaKey(certificate.publicKey as ECPublicKey, EcCurve.SECP_256_R_1)!!
            val attestedPublicKey = AttestedPublicKey(publicKey.keyId!!)
            return JWSObject(
                JWSHeader(cryptoService.jwsAlgorithm.joseType),
                Payload(attestedPublicKey.serialize().encodeToByteArray())
            ).also {
                it.sign(cryptoService.jwsContentSigner)
            }.serialize()
        } catch (e: Throwable) {
            log.warn("verifyAttestation: error", e)
            return null
        }
    }

    internal fun verifyAttestationClient(
        attestationCerts: List<ByteArray>,
        bindingCertificate: X509Certificate,
        expectedChallenge: ByteArray
    ): Boolean = if (attestationCerts.size > 1)
        verifyAttestationAndroid(attestationCerts, bindingCertificate, expectedChallenge)
    else
        verifyAttestationApple(attestationCerts.first(), expectedChallenge)

    /**
     * Verifies Google Key Attestation structure by parsing certificates
     */
    private fun verifyAttestationAndroid(
        attestationCerts: List<ByteArray>,
        bindingCertificate: X509Certificate,
        expectedChallenge: ByteArray
    ) = kotlin.runCatching {
        val certificates = attestationCerts.mapNotNull { it.parseToCertificate() }

        if (!android.verifyAttestation(certificates, clock.now().toJavaDate(), expectedChallenge)) return false

        val bindingPublicKey = bindingCertificate.publicKey.encoded
        val attestationPublicKey = certificates.first().publicKey.encoded
        return bindingPublicKey.contentEquals(attestationPublicKey)
    }.getOrElse { false }

    /**
     * Verifies Apple App Attestation structure by parsing CBOR and certificates.
     */
    private fun verifyAttestationApple(
        attestationCert: ByteArray,
        expectedChallenge: ByteArray
    ) = kotlin.runCatching {

        val result: ValidatedAttestation = attestationValidator.validate(
            attestationObject = attestationCert,
            keyIdBase64 = iosCfg.kid,
            serverChallenge = expectedChallenge,
        )

        iosCfg.iosVersion?.let {
            if (SemVer.parse(result.iOSVersion ?: return false) < SemVer.parse(it))
                return false
        }
        return true
    }.getOrElse { false }

    private fun verifyCertificateChain(certificates: List<X509Certificate>, validityDate: Date = Date()): Boolean {
        certificates.chunked(2).forEach {
            val leafCert = it.first()
            val intermediateCert = it.last()
            val signatureValid = kotlin.runCatching { leafCert.verify(intermediateCert.publicKey) }.isSuccess
            if (!signatureValid)
                return false
            val timeValid = kotlin.runCatching { leafCert.checkValidity(validityDate) }.isSuccess
            if (!timeValid)
                return false
        }
        return true
    }

    private fun ByteArray.parseToCertificate() = kotlin.runCatching {
        CertificateFactory.getInstance("X.509").generateCertificate(this.inputStream()) as X509Certificate
    }.getOrNull()

    /**
     * Root of trust for Apple's App Attestation, from
     * https://www.apple.com/certificateauthority/private/
     */
    private val appleRootCertificate = """
        MIICITCCAaegAwIBAgIQC/O+DvHN0uD7jG5yH2IXmDAKBggqhkjOPQQDAzBSMSYw
        JAYDVQQDDB1BcHBsZSBBcHAgQXR0ZXN0YXRpb24gUm9vdCBDQTETMBEGA1UECgwK
        QXBwbGUgSW5jLjETMBEGA1UECAwKQ2FsaWZvcm5pYTAeFw0yMDAzMTgxODMyNTNa
        Fw00NTAzMTUwMDAwMDBaMFIxJjAkBgNVBAMMHUFwcGxlIEFwcCBBdHRlc3RhdGlv
        biBSb290IENBMRMwEQYDVQQKDApBcHBsZSBJbmMuMRMwEQYDVQQIDApDYWxpZm9y
        bmlhMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAERTHhmLW07ATaFQIEVwTtT4dyctdh
        NbJhFs/Ii2FdCgAHGbpphY3+d8qjuDngIN3WVhQUBHAoMeQ/cLiP1sOUtgjqK9au
        Yen1mMEvRq9Sk3Jm5X8U62H+xTD3FE9TgS41o0IwQDAPBgNVHRMBAf8EBTADAQH/
        MB0GA1UdDgQWBBSskRBTM72+aEH/pwyp5frq5eWKoTAOBgNVHQ8BAf8EBAMCAQYw
        CgYIKoZIzj0EAwMDaAAwZQIwQgFGnByvsiVbpTKwSga0kP0e8EeDS4+sQmTvb7vn
        53O5+FRXgeLhpJ06ysC5PrOyAjEAp5U4xDgEgllF7En3VcE3iexZZtKeYnpqtijV
        oyFraWVIyd/dganmrduC1bmTBGwD
    """.trimIndent().decodeBase64ToArray()!!.parseToCertificate()!!

}
