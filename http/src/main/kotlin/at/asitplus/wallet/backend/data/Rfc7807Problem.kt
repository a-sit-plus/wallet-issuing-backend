package at.asitplus.wallet.backend.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { encodeDefaults = false }

@Serializable
internal class Rfc7807Problem(
    val type: String? = null,
    val title: String,
    val status: Int? = null,
    val instance: String? = null,
    val detail: String? = null,
) {
    override fun toString() = json.encodeToString(this)
}