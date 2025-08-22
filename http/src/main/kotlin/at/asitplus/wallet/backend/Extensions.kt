package at.asitplus.wallet.backend

import org.springframework.web.util.UriComponentsBuilder
import java.security.MessageDigest

object Extensions {

    fun appendPath(url: String, vararg path: String): String =
        UriComponentsBuilder.fromUriString(url).pathSegment(*path).toUriString()

    fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

}

