package at.asitplus.wallet.backend

import org.springframework.web.util.UriComponentsBuilder
import java.net.URL
import java.security.MessageDigest

object Extensions {

    fun URL.appendPath(path: String): String = UriComponentsBuilder.fromUri(toURI()).apply {
        replacePath("${toURI().path.trimEnd('/')}/${path.trimStart('/')}")
    }.toUriString()

    fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

}

