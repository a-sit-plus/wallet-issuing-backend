package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.lib.KmmResult
import at.asitplus.wallet.lib.agent.AuthenticatedCiphertext
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.Digest
import at.asitplus.wallet.lib.agent.EphemeralKeyHolder
import at.asitplus.wallet.lib.agent.JvmEphemeralKeyHolder
import at.asitplus.wallet.lib.agent.getPublicKey
import at.asitplus.wallet.lib.jws.EcCurve
import at.asitplus.wallet.lib.jws.JsonWebKey
import at.asitplus.wallet.lib.jws.JweAlgorithm
import at.asitplus.wallet.lib.jws.JweEncryption
import at.asitplus.wallet.lib.jws.JwkType
import at.asitplus.wallet.lib.jws.JwsAlgorithm
import at.asitplus.wallet.lib.jws.JwsExtensions.ensureSize
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.ECDSASigner
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.JCEECPublicKey
import org.bouncycastle.jce.spec.ECPublicKeySpec
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Extends [CryptoService] from vclib by methods needed for Java libraries
 */
interface CryptoServiceAdapter : CryptoService {
    val subjectPublicKeyInfo: SubjectPublicKeyInfo
    val jwsContentSigner: JWSSigner
    val jcaContentSigner: ContentSigner
}

class DefaultCryptoServiceAdapter(
    keyAdapter: KeyAdapter,
) : CryptoServiceAdapter {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val privateKey: PrivateKey = keyAdapter.privateKey
    private val publicKey: PublicKey = keyAdapter.publicKey
    private val provider: Provider = keyAdapter.provider
    private val jsonWebKey: JsonWebKey = keyAdapter.jsonWebKey
    override val keyId: String = jsonWebKey.keyId!!
    override val jwsAlgorithm: JwsAlgorithm = keyAdapter.jwsAlgorithm

    init {
        log.info("Loaded public key with keyId {}", keyId)
    }

    override fun toJsonWebKey() = jsonWebKey

    override suspend fun sign(input: ByteArray): KmmResult<ByteArray> = try {
        val signed = Signature.getInstance(jwsAlgorithm.jcaName, provider).apply {
            initSign(privateKey)
            update(input)
        }.sign()
        KmmResult.success(signed)
    } catch (e: Throwable) {
        KmmResult.failure(e)
    }

    override fun encrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        input: ByteArray,
        algorithm: JweEncryption
    ): KmmResult<AuthenticatedCiphertext> = try {
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
        KmmResult.success(AuthenticatedCiphertext(ciphertext, authtag))
    } catch (e: Throwable) {
        KmmResult.failure(e)
    }

    override suspend fun decrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        input: ByteArray,
        authTag: ByteArray,
        algorithm: JweEncryption
    ): KmmResult<ByteArray> = try {
        val plaintext = Cipher.getInstance(algorithm.jcaName, provider).also {
            it.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, algorithm.jcaKeySpecName),
                GCMParameterSpec(algorithm.ivLengthBits, iv)
            )
            it.updateAAD(aad)
        }.doFinal(input + authTag)
        KmmResult.success(plaintext)
    } catch (e: Throwable) {
        KmmResult.failure(e)
    }

    override fun performKeyAgreement(
        ephemeralKey: EphemeralKeyHolder,
        recipientKey: JsonWebKey,
        algorithm: JweAlgorithm
    ): KmmResult<ByteArray> {
        require(ephemeralKey is JvmEphemeralKeyHolder) { "JVM Type expected" }
        return try {
            val secret = KeyAgreement.getInstance(algorithm.jcaName, provider).also {
                it.init(ephemeralKey.keyPair.private)
                it.doPhase(recipientKey.getPublicKey(), true)
            }.generateSecret()
            KmmResult.success(secret)
        } catch (e: Throwable) {
            KmmResult.failure(e)
        }
    }

    override fun performKeyAgreement(ephemeralKey: JsonWebKey, algorithm: JweAlgorithm): KmmResult<ByteArray> {
        val parameterSpec = ECNamedCurveTable.getParameterSpec(ephemeralKey.curve?.jcaName)
        val ecPoint = parameterSpec.curve.validatePoint(BigInteger(1, ephemeralKey.x), BigInteger(1, ephemeralKey.y))
        val ecPublicKeySpec = ECPublicKeySpec(ecPoint, parameterSpec)
        val publicKey = JCEECPublicKey(ephemeralKey.type?.jcaName, ecPublicKeySpec)
        return try {
            val secret = KeyAgreement.getInstance(algorithm.jcaName, provider).also {
                it.init(privateKey)
                it.doPhase(publicKey, true)
            }.generateSecret()
            KmmResult.success(secret)
        } catch (e: Throwable) {
            KmmResult.failure(e)
        }
    }

    override fun generateEphemeralKeyPair(ecCurve: EcCurve): KmmResult<EphemeralKeyHolder> {
        return KmmResult.success(JvmEphemeralKeyHolder(ecCurve))
    }

    override fun messageDigest(input: ByteArray, digest: Digest): KmmResult<ByteArray> {
        return try {
            KmmResult.success(MessageDigest.getInstance(digest.jcaName, provider).digest(input))
        } catch (e: Throwable) {
            KmmResult.failure(e)
        }
    }

    override val jwsContentSigner: JWSSigner
        get() = ECDSASigner(privateKey as ECPrivateKey).also {
            it.jcaContext.provider = provider
        }

    override val jcaContentSigner: ContentSigner
        get() = JcaContentSignerBuilder(jwsAlgorithm.jcaName)
            .setProvider(provider)
            .build(privateKey)

    override val subjectPublicKeyInfo: SubjectPublicKeyInfo
        get() = SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(publicKey.encoded))

    private val JwkType.jcaName
        get() = when (this) {
            JwkType.EC -> "EC"
            JwkType.RSA -> "RSA"
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

val JwsAlgorithm.joseType: JWSAlgorithm
    get() = when (this) {
        JwsAlgorithm.ES256 -> JWSAlgorithm.ES256
    }

val DeviceBinding.keyId: String?
    get() = kotlin.runCatching {
        val publicKey = CertificateFactory.getInstance("X.509")
            .generateCertificate(certificate.inputStream()).publicKey
        return JsonWebKey.fromJcaKey(publicKey as ECPublicKey, EcCurve.SECP_256_R_1)!!.keyId
    }.getOrNull()

fun JsonWebKey.Companion.fromJcaKey(publicKey: ECPublicKey, ecCurve: EcCurve) =
    fromCoordinates(
        JwkType.EC,
        ecCurve,
        publicKey.w.affineX.toByteArray().ensureSize(ecCurve.coordinateLengthBytes),
        publicKey.w.affineY.toByteArray().ensureSize(ecCurve.coordinateLengthBytes)
    )