@file:OptIn(ExperimentalSerializationApi::class)

package at.asitplus.wallet.backend.config

import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.lib.JsonValueEncoder
import at.asitplus.wallet.lib.LibraryInitializer
import at.asitplus.wallet.lib.data.LocalDateOrInstant
import at.asitplus.wallet.lib.data.LocalDateOrInstantSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ValueTags
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Instant

/**
 * Value (de)serializers for the complex claim types of the EU PID, mDL and Age Verification credentials.
 *
 * Remote type metadata carries claim *names* and *display*, but not the CBOR/JSON encoding of non-primitive values
 * (dates with tag 1004, portraits, driving privileges, gender/sex enums). These were previously provided by the
 * per-credential libraries; the minimal types and serializer maps are reproduced here verbatim so this project can
 * issue those credentials without depending on the credential libraries.
 */

private const val EU_PID_NAMESPACE = "eu.europa.ec.eudi.pid.1"
private const val MDL_NAMESPACE = "org.iso.18013.5.1"

fun registerCredentialSerializers() {
    LibraryInitializer.registerCredentialSerializers(
        jsonValueEncoder = euPidJsonValueEncoder,
        itemValueSerializerMap = mapOf(
            EU_PID_NAMESPACE to mapOf(
                "birth_date" to LocalDate.serializer(),
                "sex" to UInt.serializer(),
                "nationality" to SetSerializer(String.serializer()),
                "issuance_date" to LocalDateOrInstantSerializer,
                "expiry_date" to LocalDateOrInstantSerializer,
                "portrait" to ByteArraySerializer(),
                "place_of_birth" to PlaceOfBirth.serializer(),
            )
        ),
    )
    LibraryInitializer.registerCredentialSerializers(
        jsonValueEncoder = mdlJsonValueEncoder,
        itemValueSerializerMap = mapOf(
            MDL_NAMESPACE to buildMap {
                put("birth_date", LocalDate.serializer())
                put("issue_date", LocalDate.serializer())
                put("expiry_date", LocalDate.serializer())
                put("portrait", ByteArraySerializer())
                put("driving_privileges", ArraySerializer(DrivingPrivilege.serializer()))
                put("sex", IsoSexEnumSerializer)
                put("height", UInt.serializer())
                put("weight", UInt.serializer())
                put("portrait_capture_date", LocalDate.serializer())
                put("age_in_years", UInt.serializer())
                put("age_birth_year", UInt.serializer())
                put("signature_usual_mark", ByteArraySerializer())
                ageOverElements.forEach { put(it, Boolean.serializer()) }
                put("biometric_template_face", ByteArraySerializer())
                put("biometric_template_finger", ByteArraySerializer())
                put("biometric_template_signature_sign", ByteArraySerializer())
                put("biometric_template_iris", ByteArraySerializer())
            }
        ),
    )
    LibraryInitializer.registerCredentialSerializers(
        jsonValueEncoder = { null },
        itemValueSerializerMap = mapOf(
            AV_DOCTYPE to ageOverElements.associateWith { Boolean.serializer() }
        ),
    )
}

private val ageOverElements = listOf(
    "age_over_12", "age_over_13", "age_over_14", "age_over_16", "age_over_18", "age_over_21",
    "age_over_25", "age_over_60", "age_over_62", "age_over_65", "age_over_68",
)

private val euPidJsonValueEncoder: JsonValueEncoder = {
    when (it) {
        is IsoIec5218Gender -> joseCompliantSerializer.encodeToJsonElement(it)
        is LocalDate -> joseCompliantSerializer.encodeToJsonElement(it)
        is UInt -> joseCompliantSerializer.encodeToJsonElement(it)
        is Instant -> joseCompliantSerializer.encodeToJsonElement(it)
        is LocalDateOrInstant -> joseCompliantSerializer.encodeToJsonElement(it)
        is PlaceOfBirth -> joseCompliantSerializer.encodeToJsonElement(it)
        else -> null
    }
}

private val mdlJsonValueEncoder: JsonValueEncoder = {
    when (it) {
        is DrivingPrivilege -> joseCompliantSerializer.encodeToJsonElement(it)
        is LocalDate -> joseCompliantSerializer.encodeToJsonElement(it)
        is UInt -> joseCompliantSerializer.encodeToJsonElement(it)
        else -> null
    }
}

// --- ported value types (verbatim from the credential libraries) ---

@Serializable(with = IsoIec5218GenderSerializer::class)
enum class IsoIec5218Gender(val code: UInt) {
    NOT_KNOWN(0u), MALE(1u), FEMALE(2u), OTHER(3u), INTER(4u), DIVERSE(5u), OPEN(6u), NOT_APPLICABLE(9u)
}

object IsoIec5218GenderSerializer : KSerializer<IsoIec5218Gender> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("IsoIec5218Gender", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder) =
        decoder.decodeInt().toUInt().let { code -> IsoIec5218Gender.entries.first { it.code == code } }

    override fun serialize(encoder: Encoder, value: IsoIec5218Gender) = encoder.encodeInt(value.code.toInt())
}

@Serializable
data class PlaceOfBirth(
    @SerialName("country") val country: String? = null,
    @SerialName("region") val region: String? = null,
    @SerialName("locality") val locality: String? = null,
)

enum class IsoSexEnum(val code: Int) {
    NOT_KNOWN(0), MALE(1), FEMALE(2), NOT_APPLICABLE(9);

    companion object {
        fun parseCode(code: Int) = entries.firstOrNull { it.code == code }
    }
}

object IsoSexEnumSerializer : KSerializer<IsoSexEnum?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("IsoSexEnum?", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: IsoSexEnum?) { value?.let { encoder.encodeInt(it.code) } }
    override fun deserialize(decoder: Decoder) = IsoSexEnum.parseCode(decoder.decodeInt())
}

@Serializable
data class DrivingPrivilege(
    @SerialName("vehicle_category_code") val vehicleCategoryCode: String,
    @ValueTags(1004u) @SerialName("issue_date") val issueDate: LocalDate? = null,
    @ValueTags(1004u) @SerialName("expiry_date") val expiryDate: LocalDate? = null,
    @SerialName("codes") val codes: Array<DrivingPrivilegeCode>? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as DrivingPrivilege
        if (vehicleCategoryCode != other.vehicleCategoryCode) return false
        if (issueDate != other.issueDate) return false
        if (expiryDate != other.expiryDate) return false
        if (codes != null) {
            if (other.codes == null) return false
            if (!codes.contentEquals(other.codes)) return false
        } else if (other.codes != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = vehicleCategoryCode.hashCode()
        result = 31 * result + (issueDate?.hashCode() ?: 0)
        result = 31 * result + (expiryDate?.hashCode() ?: 0)
        result = 31 * result + (codes?.contentHashCode() ?: 0)
        return result
    }
}

@Serializable
data class DrivingPrivilegeCode(
    @SerialName("code") val code: String,
    @SerialName("sign") val sign: String? = null,
    @SerialName("value") val value: String? = null,
)
