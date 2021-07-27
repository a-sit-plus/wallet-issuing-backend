package at.asitplus.wallet.backend.data

import com.nimbusds.jose.Payload
import com.nimbusds.jose.util.Base64URL
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

@Serializable
data class VerifiableCredential(
    @SerialName("id")
    val id: String,
    @SerialName("type")
    val type: Array<String>,
    @SerialName("issuer")
    val issuer: String,
    @Serializable(with = InstantStringSerializer::class)
    @SerialName("issuanceDate")
    val issuanceDate: Instant,
    @Serializable(with = NullableInstantStringSerializer::class)
    @SerialName("expirationDate")
    val expirationDate: Instant?,
    @SerialName("revocation")
    val revocation: Revocation?,
    @SerialName("credentialSubject")
    val credentialSubject: Subject,
) {
    constructor(
        id: String,
        issuer: String,
        lifetime: Duration,
        revocation: Revocation,
        credentialSubject: Subject
    ) : this(
        id,
        arrayOf("VerifiableCredential"),
        issuer,
        Instant.now(),
        Instant.now() + lifetime,
        revocation,
        credentialSubject,
    )

    fun toJws() = VerifiableCredentialJws(
        this,
        credentialSubject.id,
        issuanceDate,
        issuer,
        expirationDate,
        id
    )
}

@Serializable
data class VerifiableCredentialJws(
    @SerialName("vc")
    val vc: VerifiableCredential,
    @SerialName("sub")
    val subject: String,
    @SerialName("nbf")
    @Serializable(with = InstantLongSerializer::class)
    val notBefore: Instant,
    @SerialName("iss")
    val issuer: String,
    @SerialName("exp")
    @Serializable(with = NullableInstantLongSerializer::class)
    val expiration: Instant?,
    @SerialName("jti")
    val jwtId: String
) {

    fun toJwsPayload() = Payload(Base64URL.encode(serialize()))

    fun serialize() = jsonSerializer.encodeToString(this)

    companion object {
        fun fromPayload(it: Payload) = deserialize(it.toBase64URL().decodeToString())

        fun deserialize(it: String) = jsonSerializer.decodeFromString<VerifiableCredentialJws>(it)
    }

}

@JvmInline
value class VerifiableCredentialSerialized(val compactJws: String)

@Serializable
data class Revocation(
    @SerialName("id")
    val id: String,
    @SerialName("type")
    val type: String
)

@Serializable
data class PupilId(
    @SerialName("fn")
    val firstname: String,
    @SerialName("ln")
    val lastname: String,
)

@Serializable
data class Subject(
    @SerialName("id")
    val id: String,
    @SerialName("pupilId")
    val pupilId: PupilId,
)

@Serializable
data class VerifiablePresentationParsed(
    @SerialName("id")
    val id: String,
    @SerialName("type")
    val type: String,
    @SerialName("verifiableCredential")
    val verifiableCredential: Array<VerifiableCredentialJws>,
)

// TODO Vorsehen, dass das schon ein JSON-LD sein könnte?
@Serializable
data class VerifiablePresentation(
    @SerialName("id")
    val id: String,
    @SerialName("type")
    val type: String,
    @SerialName("verifiableCredential")
    val verifiableCredential: Array<String>,
) {
    constructor(id: String, verifiableCredential: Array<String>) : this(
        id,
        "VerifiablePresentation",
        verifiableCredential
    )

    fun toJws(
        subject: Agent,
        challenge: String,
        audience: Agent,
    ) = VerifiablePresentationJws(
        this,
        challenge,
        subject.keyId,
        audience.keyId,
        id
    )
}

// TODO Vorsehen, dass das JSON-LD-Proof sein könnte?

@Serializable
data class VerifiablePresentationJws(
    @SerialName("vp")
    val vp: VerifiablePresentation,
    @SerialName("nonce")
    val challenge: String,
    @SerialName("iss")
    val issuer: String,
    @SerialName("aud")
    val audience: String,
    @SerialName("jti")
    val jwtId: String
) {

    fun toJwsPayload() = Payload(Base64URL.encode(serialize()))

    fun serialize() = jsonSerializer.encodeToString(this)

    companion object {
        fun fromPayload(it: Payload) = deserialize(it.toBase64URL().decodeToString())

        fun deserialize(it: String) = jsonSerializer.decodeFromString<VerifiablePresentationJws>(it)
    }

}

@JvmInline
value class VerifiablePresentationSerialized(val compactJws: String)


private val jsonSerializer = Json { prettyPrint = false; encodeDefaults = false }
