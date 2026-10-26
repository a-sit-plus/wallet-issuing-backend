package at.asitplus.wallet.backend.config

import java.security.MessageDigest
import kotlin.io.encoding.Base64

/**
 * The `vct#integrity` of each SD-JWT credential, i.e. the integrity of the type metadata document its `vct` resolves
 * to, which the ETSI SD-JWT VC profile requires in issued credentials.
 */
class TypeMetadataIntegrity(private val byVct: Map<String, String>) {

    fun forVct(vct: String): String? = byVct[vct]

    companion object {
        /** [W3C SRI](https://www.w3.org/TR/sri/), 3.3.5: the hash algorithm, a dash and the base64 digest. */
        fun of(document: ByteArray): String =
            "sha256-" + Base64.encode(MessageDigest.getInstance("SHA-256").digest(document))
    }
}
