package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.DefaultKeyIdService
import at.asitplus.wallet.lib.agent.KeyIdService
import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.ECDHDecrypter
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
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
    keyIdService: KeyIdService = DefaultKeyIdService()
) : CryptoService {

    private val privateKey: PrivateKey
    private val publicKey: PublicKey

    init {
        val privateKeyString = loadResource(resourceLoader, config.privateKey.toString())
        val privateKeyRead = PEMParser(StringReader(privateKeyString)).readObject()
        privateKey = JcaPEMKeyConverter().getPrivateKey(privateKeyRead as PrivateKeyInfo)
        val publicKeyString = loadResource(resourceLoader, config.publicKey.toString())
        val publicKeyRead = PEMParser(StringReader(publicKeyString)).readObject()
        publicKey = JcaPEMKeyConverter().getPublicKey(publicKeyRead as SubjectPublicKeyInfo)
    }

    private fun loadResource(resourceLoader: ResourceLoader, path: String) =
        StreamUtils.copyToString(resourceLoader.getResource(path).inputStream, Charset.defaultCharset())


    override val jwk: JWK = ECKey.Builder(Curve.P_256, publicKey as ECPublicKey).build()

    override val keyId = keyIdService.calcKeyId(publicKey)

    override fun buildJwsHeader(): JWSHeader.Builder {
        return JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).jwk(jwk)
    }

    override fun buildJweHeader(): JWEHeader.Builder {
        return JWEHeader.Builder(JWEAlgorithm.ECDH_ES_A256KW, EncryptionMethod.A256GCM).keyID(keyId).jwk(jwk)
    }

    override suspend fun signJwsObject(jwsObject: JWSObject): JWSObject = suspendCoroutine {
        try {
            jwsObject.sign(ECDSASigner(privateKey as ECPrivateKey))
            it.resumeWith(Result.success(jwsObject))
        } catch (e: Throwable) {
            it.resumeWith(Result.failure(e))
        }
    }

    override suspend fun decryptJweObject(jweObject: JWEObject): JWEObject = suspendCoroutine {
        try {
            jweObject.decrypt(ECDHDecrypter(publicKey as ECPrivateKey))
            it.resumeWith(Result.success(jweObject))
        } catch (e: Throwable) {
            it.resumeWith(Result.failure(e))
        }
    }

}