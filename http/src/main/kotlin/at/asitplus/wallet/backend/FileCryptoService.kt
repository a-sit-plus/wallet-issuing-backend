package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.KeyIdService
import at.asitplus.wallet.lib.KeyIdServiceDummy
import at.asitplus.wallet.lib.PublicKeyHolder
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.jvm.PublicKeyHolderJvm
import at.asitplus.wallet.lib.jws.JsonWebKey
import at.asitplus.wallet.lib.jws.JweAlgorithm
import at.asitplus.wallet.lib.jws.JweEncrypted
import at.asitplus.wallet.lib.jws.JweEncryption
import at.asitplus.wallet.lib.jws.JweHeader
import at.asitplus.wallet.lib.jws.JweHeaderAndPayload
import at.asitplus.wallet.lib.jws.JwkType
import at.asitplus.wallet.lib.jws.JwsAlgorithm
import at.asitplus.wallet.lib.jws.JwsContentType
import at.asitplus.wallet.lib.jws.JwsHeader
import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDHDecrypter
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.ECKey
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.slf4j.LoggerFactory
import org.springframework.core.io.ResourceLoader
import org.springframework.util.StreamUtils
import java.io.StringReader
import java.nio.charset.Charset
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import kotlin.coroutines.suspendCoroutine

class FileCryptoService(
    config: KeyFileConfiguration,
    resourceLoader: ResourceLoader,
    keyIdService: KeyIdService = KeyIdServiceDummy()
) : CryptoService {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    private val privateKey: PrivateKey
    private val publicKey: PublicKey

    init {
        val privateKeyString = loadResource(resourceLoader, config.privateKey.toString())
        val privateKeyRead = PEMParser(StringReader(privateKeyString)).readObject()
        privateKey = JcaPEMKeyConverter().getPrivateKey(privateKeyRead as PrivateKeyInfo)
        val publicKeyString = loadResource(resourceLoader, config.publicKey.toString())
        val publicKeyRead = PEMParser(StringReader(publicKeyString)).readObject()
        publicKey = JcaPEMKeyConverter().getPublicKey(publicKeyRead as SubjectPublicKeyInfo)
        logger.info("Loaded public key with keyId ${keyIdService.calcKeyId(PublicKeyHolderJvm(publicKey))}")
        require(publicKey != null)
    }

    private fun loadResource(resourceLoader: ResourceLoader, path: String) =
        StreamUtils.copyToString(resourceLoader.getResource(path).inputStream, Charset.defaultCharset())


    override val keyId = keyIdService.calcKeyId(PublicKeyHolderJvm(publicKey))!!

    override fun buildJweHeader(type: JwsContentType, contentType: JwsContentType?): JweHeader {
        return JweHeader(JweAlgorithm.ECDH_ES_A256KW, JweEncryption.A256GCM, keyId, contentType = contentType, type = type)
    }

    override fun buildJwsHeader(type: JwsContentType, contentType: JwsContentType?): JwsHeader {
        return JwsHeader(JwsAlgorithm.ES256, keyId, contentType, type)
    }

    override fun toJsonWebKey() = JsonWebKey(
        type = JwkType.EC,
        curve = "P-256",
        keyId = keyId,
        x = ECKey.encodeCoordinate(256, (publicKey as ECPublicKey).w.affineX).decode(),
        y = ECKey.encodeCoordinate(256, (publicKey as ECPublicKey).w.affineY).decode(),
    )

    override suspend fun signJwsObject(jwsHeader: JwsHeader, jwsPayload: ByteArray): String = suspendCoroutine {
        try {
            val jws = JWSObject(JWSHeader.parse(jwsHeader.serialize()), Payload(jwsPayload)).also {
                it.sign(ECDSASigner(privateKey as ECPrivateKey))
            }
            it.resumeWith(Result.success(jws.serialize()))
        } catch (e: Throwable) {
            it.resumeWith(Result.failure(e))
        }
    }

    override suspend fun decryptJweObject(jweObject: JweEncrypted, serialized: String): JweHeaderAndPayload {
        val decrypter = ECDHDecrypter(privateKey as ECPrivateKey)
        val jweObjectParsed = JWEObject.parse(serialized).also {
            it.decrypt(decrypter)
        }
        return JweHeaderAndPayload(jweObject.header, jweObjectParsed.payload.toBytes())
    }

}