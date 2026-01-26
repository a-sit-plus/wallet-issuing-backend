package at.asitplus.wallet.backend

import org.springframework.web.util.UriComponentsBuilder
import java.net.URL
import java.security.MessageDigest

object Extensions {

    fun URL.appendPath(path: String) = UriComponentsBuilder.fromUri(toURI()).path(path).toUriString()

    fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

}


