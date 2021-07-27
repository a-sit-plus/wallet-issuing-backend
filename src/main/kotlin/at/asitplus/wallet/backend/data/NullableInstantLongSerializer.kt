package at.asitplus.wallet.backend.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

class NullableInstantLongSerializer : KSerializer<Instant?> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant?", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Instant? {
        return try {
            Instant.ofEpochSecond(decoder.decodeLong())
        } catch (e: Exception) {
            null
        }
    }

    override fun serialize(encoder: Encoder, value: Instant?) {
        value?.let { encoder.encodeLong(it.epochSecond) }
    }

}