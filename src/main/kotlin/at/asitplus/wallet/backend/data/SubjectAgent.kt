package at.asitplus.wallet.backend.data

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

data class StoreEntry(
    val serialized: VerifiableCredentialSerialized,
    val vc: VerifiableCredentialJws
)

open class Agent {

    protected val keyPair: KeyPair = KeyPairGenerator.getInstance("EC").also {
        it.initialize(256)
    }.genKeyPair()

    val keyId = calcKeyId(keyPair.public)

    private fun calcKeyId(publicKey: PublicKey) = "urn:keyid:" + sha256(publicKey.encoded).toBase64Url()

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").also { it.update(bytes) }.digest()

    private val vcStore = mutableListOf<StoreEntry>()

    fun issueCredential(agent: Agent): VerifiableCredentialSerialized {
        val id = "example.com/credential/1234"
        val subject = Subject(agent.keyId, PupilId("Max", "Mustermann"))
        val revocation = Revocation("example.com/rev/1", "RevocationList2020")
        val vc = VerifiableCredential(id, keyId, Duration.ofSeconds(60), revocation, subject)
        val jwsHeader = buildJwsHeader()
        val jwsPayload = vc.toJws().toJwsPayload()
        return VerifiableCredentialSerialized(createSignedJwt(jwsHeader, jwsPayload))
    }

    fun storeCredential(it: VerifiableCredentialSerialized) {
        vcStore += StoreEntry(it, parseVc(it.compactJws))
    }

    fun createPresentation(challenge: String, audience: Agent): VerifiablePresentationSerialized {
        val id = "example.com/presentation/5678"
        val vp = VerifiablePresentation(id, vcStore.map { it.serialized.compactJws }.toTypedArray())
        val jwsHeader = buildJwsHeader()
        val jwsPayload = vp.toJws(this, challenge, audience).toJwsPayload()
        return VerifiablePresentationSerialized(createSignedJwt(jwsHeader, jwsPayload))
    }

    fun verifyPresentation(vp: VerifiablePresentationSerialized, challenge: String): VerifiablePresentationParsed {
        val parsedVp = parseVp(vp.compactJws, challenge)
        val parsedVcs = parsedVp.verifiableCredential.map { parseVc(it) }
        return VerifiablePresentationParsed(parsedVp.id, parsedVp.type, parsedVcs.toTypedArray())
    }

    private fun parseVp(it: String, challenge: String): VerifiablePresentation {
        val jws = JWSObject.parse(it)
        val publicKey = jws.header.jwk.toECKey().toECPublicKey()
        val verifier = DefaultJWSVerifierFactory().createJWSVerifier(jws.header, publicKey)
        if (!jws.verify(verifier))
            throw IllegalArgumentException()
        val vpJws = VerifiablePresentationJws.fromPayload(jws.payload)
        if (vpJws.challenge != challenge)
            throw IllegalArgumentException("nonce")
        if (vpJws.audience != keyId)
            throw IllegalArgumentException("aud")
        if (vpJws.issuer != calcKeyId(publicKey))
            throw IllegalArgumentException("iss")
        if (vpJws.jwtId != vpJws.vp.id)
            throw IllegalArgumentException("jti")
        if (vpJws.vp.type != "VerifiablePresentation")
            throw IllegalArgumentException("type")
        return vpJws.vp
    }

    private fun parseVc(it: String): VerifiableCredentialJws {
        val jws = JWSObject.parse(it)
        val publicKey = jws.header.jwk.toECKey().toECPublicKey()
        val verifier = DefaultJWSVerifierFactory().createJWSVerifier(jws.header, publicKey)
        if (!jws.verify(verifier)) throw IllegalArgumentException()
        val vcJws = VerifiableCredentialJws.fromPayload(jws.payload)
        if (vcJws.issuer != calcKeyId(publicKey))
            throw IllegalArgumentException("iss")
        if (vcJws.issuer != vcJws.vc.issuer)
            throw IllegalArgumentException("iss")
        if (vcJws.jwtId != vcJws.vc.id)
            throw IllegalArgumentException("jti")
        if (vcJws.subject != vcJws.vc.credentialSubject.id)
            throw IllegalArgumentException("sub")
        if (!vcJws.vc.type.contains("VerifiableCredential"))
            throw IllegalArgumentException("type")
        if (vcJws.expiration != null && vcJws.expiration.isBefore(Instant.now()))
            throw IllegalArgumentException("exp")
        if (vcJws.vc.expirationDate != null && vcJws.vc.expirationDate.isBefore(Instant.now()))
            throw IllegalArgumentException("expirationDate")
        if (vcJws.expiration?.truncatedTo(ChronoUnit.SECONDS) != vcJws.vc.expirationDate?.truncatedTo(ChronoUnit.SECONDS))
            throw IllegalArgumentException("exp")
        if (vcJws.notBefore.isAfter(Instant.now()))
            throw IllegalArgumentException("nbf")
        if (vcJws.vc.issuanceDate.isAfter(Instant.now()))
            throw IllegalArgumentException("issuanceDate")
        if (vcJws.notBefore.truncatedTo(ChronoUnit.SECONDS) != vcJws.vc.issuanceDate.truncatedTo(ChronoUnit.SECONDS))
            throw IllegalArgumentException("nbf")
        return vcJws
    }

    private fun createSignedJwt(
        jwsHeader: JWSHeader,
        jwsPayload: Payload
    ) = JWSObject(jwsHeader, jwsPayload).also {
        it.sign(ECDSASigner(keyPair.private as ECPrivateKey))
    }.serialize()

    private fun buildJwsHeader() = JWSHeader.Builder(JWSAlgorithm.ES256)
        .type(JOSEObjectType.JWT)
        .keyID(keyId)
        .jwk(ECKey.Builder(Curve.P_256, keyPair.public as ECPublicKey).build())
        .build()

}
