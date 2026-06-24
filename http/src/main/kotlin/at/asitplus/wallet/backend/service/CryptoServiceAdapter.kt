package at.asitplus.wallet.backend.service

import at.asitplus.KmmResult
import at.asitplus.wallet.backend.pki.KeyAdapter
import at.asitplus.wallet.lib.agent.*
import at.asitplus.wallet.lib.cbor.CoseAlgorithm
import at.asitplus.wallet.lib.jws.*
import at.asitplus.wallet.lib.jws.JwsExtensions.ensureSize
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.ECDSASigner
import io.github.aakira.napier.Napier
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.JCEECPublicKey
import org.bouncycastle.jce.spec.ECPublicKeySpec
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
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
    val x509Certificate: X509Certificate?
}

class DefaultCryptoServiceAdapter(
    keyAdapter: KeyAdapter,
) : CryptoServiceAdapter {

    private val privateKey: PrivateKey = keyAdapter.privateKey
    private val publicKey: PublicKey = keyAdapter.publicKey
    private val provider: Provider = keyAdapter.provider
    private val jsonWebKey: JsonWebKey = keyAdapter.jsonWebKey
    override val jwsAlgorithm: JwsAlgorithm = keyAdapter.jwsAlgorithm
    override val coseAlgorithm: CoseAlgorithm = keyAdapter.coseAlgorithm
    override val x509Certificate = keyAdapter.certificate
    override val certificate: ByteArray? = keyAdapter.certificate?.encoded
    override fun toPublicKey() = jsonWebKey.toCryptoPublicKey()!!


    init {
        Napier.i("Loaded public key with id ${jsonWebKey.identifier}")
    }

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

}

val JwsAlgorithm.joseType: JWSAlgorithm
    get() = when (this) {
        JwsAlgorithm.ES256 -> JWSAlgorithm.ES256
        JwsAlgorithm.ES384 -> JWSAlgorithm.ES384
        JwsAlgorithm.ES512 -> JWSAlgorithm.ES512
        JwsAlgorithm.HMAC256 -> JWSAlgorithm.HS256
    }

fun JsonWebKey.Companion.fromJcaKey(publicKey: ECPublicKey, ecCurve: EcCurve) =
    fromCoordinates(
        JwkType.EC,
        ecCurve,
        publicKey.w.affineX.toByteArray().ensureSize(ecCurve.coordinateLengthBytes),
        publicKey.w.affineY.toByteArray().ensureSize(ecCurve.coordinateLengthBytes)
    )