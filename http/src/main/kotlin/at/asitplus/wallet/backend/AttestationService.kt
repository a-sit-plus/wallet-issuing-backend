package at.asitplus.wallet.backend

import at.asitplus.attestation.android.AndroidAttestationChecker
import at.asitplus.attestation.android.AndroidAttestationConfiguration
import at.asitplus.wallet.backend.config.IOSAttestationConfigurationProperties
import at.asitplus.wallet.lib.encodeBase64
import at.asitplus.wallet.lib.toJavaClock
import at.asitplus.wallet.lib.toJavaDate
import ch.veehait.devicecheck.appattest.AppleAppAttest
import ch.veehait.devicecheck.appattest.attestation.ValidatedAttestation
import ch.veehait.devicecheck.appattest.common.App
import ch.veehait.devicecheck.appattest.common.AppleAppAttestEnvironment
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.datetime.Clock
import net.swiftzer.semver.SemVer
import org.bouncycastle.cert.X509CertificateHolder
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.time.Duration
import kotlin.time.toJavaDuration

interface AttestationService {

    /**
     * Verifies the Android Key Attestation or Apple App Attestation
     * structures of the client (in [attestationCerts]),
     * creating a signed public key (with data from [bindingCertificate])
     * if the device can be verified and [challenge] matches the attestation challenge.
     */
    fun verifyAttestation(
        attestationCerts: List<ByteArray>,
        bindingPublicKey: ByteArray,
        challenge: ByteArray
    ): Boolean

}


object NoopAttestationService : AttestationService {

    private val log = LoggerFactory.getLogger(this.javaClass)
    override fun verifyAttestation(
        attestationCerts: List<ByteArray>,
        bindingPublicKey: ByteArray,
        challenge: ByteArray
    ) = true


}

class DefaultAttestationService(
    androidAttestationConfiguration: AndroidAttestationConfiguration,
    private val iosCfg: IOSAttestationConfigurationProperties,
    private val clock: Clock,
    private val verificationTimeOffset: Duration
) : AttestationService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val android =
        AndroidAttestationChecker(androidAttestationConfiguration) { expected, actual -> expected contentEquals actual }

    private val appleAppAttest = AppleAppAttest(
        app = App(iosCfg.teamIdentifier, iosCfg.bundleIdentifier),
        appleAppAttestEnvironment = if (iosCfg.sandbox) AppleAppAttestEnvironment.DEVELOPMENT else AppleAppAttestEnvironment.PRODUCTION,
    )


    private val appAttestReader = ObjectMapper(CBORFactory())
        .registerKotlinModule()
        .readerFor(AttestationObject::class.java)
    private val attestationValidator = appleAppAttest.createAttestationValidator(
        clock = java.time.Clock.offset(
            clock.toJavaClock(),
            verificationTimeOffset.toJavaDuration()
        )
    )


    /**
     * Verifies the Android Key Attestation or Apple App Attestation
     * structures of the client (in [attestationCerts]),
     * creating a signed public key (with data from [bindingCertificate])
     * if the device can be verified.
     */
    override fun verifyAttestation(
        attestationCerts: List<ByteArray>,
        bindingPublicKey: ByteArray,
        challenge: ByteArray
    ): Boolean {
        return try {
            log.debug("attestation certificate chain length: ${attestationCerts.size}")

            verifyAttestationClient(attestationCerts, bindingPublicKey, challenge).also {
                if (!it) log.error("Could not verify attestation chain: {}", attestationCerts.map { it.encodeBase64() })
            }
        } catch (e: Throwable) {
            false.also { log.warn("verifyAttestation: error", e) }
        }
    }

    internal fun verifyAttestationClient(
        attestationCerts: List<ByteArray>,
        bindingPublicKey: ByteArray,
        expectedChallenge: ByteArray
    ): Boolean = if (attestationCerts.size > 1)
        verifyAttestationAndroid(attestationCerts, bindingPublicKey, expectedChallenge)
    else
        verifyAttestationApple(attestationCerts.first(), expectedChallenge)

    /**
     * Verifies Google Key Attestation structure by parsing certificates
     */
    private fun verifyAttestationAndroid(
        attestationCerts: List<ByteArray>,
        bindingPublicKey: ByteArray,
        expectedChallenge: ByteArray
    ) = kotlin.runCatching {
        log.debug("Verifying Android attestation")
        val certificates = attestationCerts.mapNotNull { it.parseToCertificate() }

        if (certificates.size != attestationCerts.size) return false.also { log.warn("Could not parse attestation chain") }

        //throws exception on fail
        android.verifyAttestation(certificates, (clock.now() + verificationTimeOffset).toJavaDate(), expectedChallenge)

        val attestationPublicKey = certificates.first().publicKey.encoded
        return bindingPublicKey.contentEquals(attestationPublicKey)
            .also { if (!it) log.warn("binding public key ${bindingPublicKey.encodeBase64()} does not equal attestation public key ${attestationPublicKey.encodeBase64()} from certificate") }
    }.getOrElse {
        log.warn("Android attestation error", it)
        false
    }

    /**
     * Verifies Apple App Attestation structure by parsing CBOR and certificates.
     */
    private fun verifyAttestationApple(
        attestationObject: ByteArray,
        expectedChallenge: ByteArray
    ) = kotlin.runCatching {
        log.debug("Verifying iOS attestation")

        val parsedAttestationCert =
            X509CertificateHolder(appAttestReader.readValue<AttestationObject>(attestationObject).attStmt.x5c.first())

        val result: ValidatedAttestation = attestationValidator.validate(
            attestationObject = attestationObject,
            keyIdBase64 = MessageDigest.getInstance("SHA-256")
                .digest(parsedAttestationCert.subjectPublicKeyInfo.publicKeyData.bytes)
                .encodeBase64(),
            serverChallenge = expectedChallenge,
        )

        iosCfg.iosVersion?.let {
            val parsedVersion = SemVer.parse(
                result.iOSVersion ?: return false.also { log.warn("Could not parse iOS version from AppAttest") })
            val configuredVersion = SemVer.parse(it)
            if (parsedVersion < configuredVersion)
                return false.also { log.warn("iOS version  $parsedVersion <$configuredVersion") }
        }
        return true
    }.getOrElse {
        log.warn("iOS Attestation error", it)
        false
    }
}

//copied from AppAttest Library
private val certificateFactory = CertificateFactory.getInstance("X.509")
fun ByteArray.parseToCertificate(): X509Certificate? = kotlin.runCatching {
    certificateFactory.generateCertificate(this.inputStream()) as X509Certificate
}.getOrNull()

data class AttestationObject(
    val fmt: String,
    val attStmt: AttestationStatement,
    val authData: ByteArray
) {
    data class AttestationStatement(
        val x5c: List<ByteArray>,
        val receipt: ByteArray
    ) {
    }
}