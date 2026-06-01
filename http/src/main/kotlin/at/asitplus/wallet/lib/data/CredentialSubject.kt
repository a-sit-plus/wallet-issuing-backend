package at.asitplus.wallet.lib.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Binary-compatibility shim for credential libraries compiled against vck ≤5.12.0.
 * CredentialSubject was removed in vck 5.13.0; this stub keeps older library binaries loadable
 * until all credential libraries are updated to vck 5.13.0-compatible releases.
 */
@Suppress("DEPRECATION")
@Deprecated("Removed in vck 5.13.0. Use JsonElement for credential subjects instead.")
@Serializable
abstract class CredentialSubject {
    @SerialName("id")
    abstract val id: String
}
