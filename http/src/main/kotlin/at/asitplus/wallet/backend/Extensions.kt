package at.asitplus.wallet.backend

import org.springframework.web.util.UriComponentsBuilder
import java.security.MessageDigest

object Extensions {

    fun String.appendPath(path: String) = UriComponentsBuilder.fromUriString(this).path(path).toUriString()

    fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

}


