package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.DefaultKeyIdService
import at.asitplus.wallet.lib.JvmPublicKeyHolder
import at.asitplus.wallet.lib.KeyIdService
import at.asitplus.wallet.lib.PublicKeyHolder
import at.asitplus.wallet.lib.agent.AuthenticatedCiphertext
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.Digest
import at.asitplus.wallet.lib.agent.EphemeralKeyHolder
import at.asitplus.wallet.lib.agent.JvmEphemeralKeyHolder
import at.asitplus.wallet.lib.jws.EcCurve
import at.asitplus.wallet.lib.jws.JsonWebKey
import at.asitplus.wallet.lib.jws.JweAlgorithm
import at.asitplus.wallet.lib.jws.JweEncryption
import at.asitplus.wallet.lib.jws.JwkType
import at.asitplus.wallet.lib.jws.JwsAlgorithm
import at.asitplus.wallet.lib.jws.JwsExtensions.convertToAsn1Signature
import at.asitplus.wallet.lib.jws.JwsExtensions.ensureSize
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.JCEECPublicKey
import org.bouncycastle.jce.spec.ECPublicKeySpec
import org.slf4j.LoggerFactory
import org.springframework.core.io.ResourceLoader
import org.springframework.util.StreamUtils
import java.math.BigInteger
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.suspendCoroutine

class FileCryptoService(
    keyAdapter: KeyAdapter,
    keyIdService: KeyIdService = DefaultKeyIdService()
) : CryptoService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val privateKey: PrivateKey = keyAdapter.privateKey
    private val publicKey: PublicKey = keyAdapter.publicKey
    private val ecCurve: EcCurve = keyAdapter.ecCurve
    private val provider: String = keyAdapter.provider
    override val keyId: String = keyIdService.calcKeyId(JvmPublicKeyHolder(publicKey, ecCurve))!!
    override val jwsAlgorithm: JwsAlgorithm = keyAdapter.jwsAlgorithm

    init {
        log.info("Loaded public key with keyId $keyId")
    }

    private fun loadResource(resourceLoader: ResourceLoader, path: String) =
        StreamUtils.copyToString(resourceLoader.getResource(path).inputStream, Charset.defaultCharset())

    override fun toJsonWebKey() = JsonWebKey(
        type = JwkType.EC,
        curve = ecCurve,
        keyId = keyId,
        x = (publicKey as ECPublicKey).w.affineX.toByteArray().ensureSize(ecCurve.coordinateLengthBytes),
        y = (publicKey as ECPublicKey).w.affineY.toByteArray().ensureSize(ecCurve.coordinateLengthBytes)
    )

    override fun verify(
        input: ByteArray,
        signature: ByteArray,
        algorithm: JwsAlgorithm,
        publicKey: PublicKeyHolder
    ): Boolean {
        require(publicKey is JvmPublicKeyHolder) { "JVM Type expected" }
        val asn1Signature = signature.convertToAsn1Signature(ecCurve.signatureLengthBytes)
        return Signature.getInstance(algorithm.jcaName, provider).apply {
            initVerify(publicKey.publicKey)
            update(input)
        }.verify(asn1Signature)
    }

    override suspend fun sign(input: ByteArray): ByteArray = suspendCoroutine {
        try {
            val signed = Signature.getInstance(jwsAlgorithm.jcaName, provider).apply {
                initSign(privateKey)
                update(input)
            }.sign()
            it.resumeWith(Result.success(signed))
        } catch (e: Throwable) {
            it.resumeWith(Result.failure(e))
        }
    }

    override fun encrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        input: ByteArray,
        algorithm: JweEncryption
    ): AuthenticatedCiphertext {
        val jcaCiphertext = Cipher.getInstance(algorithm.jcaName, provider).also {
            it.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, algorithm.jcaKeySpecName),
                GCMParameterSpec(algorithm.ivLengthBits, iv)
            )
            it.updateAAD(aad)
        }.doFinal(input)
        val ciphertext = jcaCiphertext.dropLast(algorithm.ivLengthBits / 8).toByteArray()
        val authtag = jcaCiphertext.takeLast(algorithm.ivLengthBits / 8).toByteArray()
        return AuthenticatedCiphertext(ciphertext, authtag)
    }

    override suspend fun decrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        input: ByteArray,
        authTag: ByteArray,
        algorithm: JweEncryption
    ): ByteArray? = suspendCoroutine {
        try {
            val plaintext = Cipher.getInstance(algorithm.jcaName, provider).also {
                it.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(key, algorithm.jcaKeySpecName),
                    GCMParameterSpec(algorithm.ivLengthBits, iv)
                )
                it.updateAAD(aad)
            }.doFinal(input + authTag)
            it.resumeWith(Result.success(plaintext))
        } catch (e: Throwable) {
            it.resumeWith(Result.failure(e))
        }
    }

    override fun performKeyAgreement(
        ephemeralKey: EphemeralKeyHolder,
        recipientKey: PublicKeyHolder,
        algorithm: JweAlgorithm
    ): ByteArray {
        require(ephemeralKey is JvmEphemeralKeyHolder) { "JVM Type expected" }
        require(recipientKey is JvmPublicKeyHolder) { "JVM Type expected" }
        return KeyAgreement.getInstance(algorithm.jcaName, provider).also {
            it.init(ephemeralKey.keyPair.private)
            it.doPhase(recipientKey.publicKey, true)
        }.generateSecret()
    }

    override fun performKeyAgreement(ephemeralKey: JsonWebKey, algorithm: JweAlgorithm): ByteArray {
        val parameterSpec = ECNamedCurveTable.getParameterSpec(ephemeralKey.curve?.jcaName)
        val ecPoint = parameterSpec.curve.validatePoint(BigInteger(1, ephemeralKey.x), BigInteger(1, ephemeralKey.y))
        val ecPublicKeySpec = ECPublicKeySpec(ecPoint, parameterSpec)
        val publicKey = JCEECPublicKey("EC", ecPublicKeySpec)
        return KeyAgreement.getInstance(algorithm.jcaName, provider).also {
            it.init(privateKey)
            it.doPhase(publicKey, true)
        }.generateSecret()
    }

    override fun generateEphemeralKeyPair(ecCurve: EcCurve): EphemeralKeyHolder {
        return JvmEphemeralKeyHolder(ecCurve)
    }

    override fun messageDigest(input: ByteArray, digest: Digest): ByteArray {
        return MessageDigest.getInstance(digest.jcaName, provider).digest(input)
    }

    private val JwsAlgorithm.jcaName
        get() = when (this) {
            JwsAlgorithm.ES256 -> "SHA256withECDSA"
        }

    private val Digest.jcaName
        get() = when (this) {
            Digest.SHA256 -> "SHA-256"
        }

    private val JweEncryption.jcaName
        get() = when (this) {
            JweEncryption.A256GCM -> "AES/GCM/NoPadding"
        }

    private val JweEncryption.jcaKeySpecName
        get() = when (this) {
            JweEncryption.A256GCM -> "AES"
        }

    private val JweAlgorithm.jcaName
        get() = when (this) {
            JweAlgorithm.ECDH_ES -> "ECDH"
        }

    private val EcCurve.jcaName
        get() = when (this) {
            EcCurve.SECP_256_R_1 -> "secp256r1"
        }
}