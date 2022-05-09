package at.asitplus.wallet.backend

import org.springframework.web.util.UriComponentsBuilder

object Extensions {

    fun appendPath(url: String, vararg path: String) =
        UriComponentsBuilder.fromHttpUrl(url).pathSegment(*path).toUriString()

}