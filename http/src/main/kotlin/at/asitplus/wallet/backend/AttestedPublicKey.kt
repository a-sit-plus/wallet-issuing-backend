package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.jws.JsonWebKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Structure for the "attestedPublicKey" format for apps,
 * may be moved into the PupilIdKmmLibrary.
 */
@Serializable
data class AttestedPublicKey(
    @SerialName("jwk")
    val jsonWebKey: JsonWebKey,
    @SerialName("sn")
    val serialNumber: Long,
) {
    fun serialize() = Json.encodeToString(this)
}