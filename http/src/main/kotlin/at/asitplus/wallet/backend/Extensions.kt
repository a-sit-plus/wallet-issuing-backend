package at.asitplus.wallet.backend

import at.asitplus.KmmResult
import org.springframework.web.util.UriComponentsBuilder

object Extensions {

    fun appendPath(url: String, vararg path: String) =
        UriComponentsBuilder.fromHttpUrl(url).pathSegment(*path).toUriString()

}

/**
 * needs to go once kmmresult can be updatedd to 1.1
 */
@Suppress("UNCHECKED_CAST")
inline fun <R,T> KmmResult<T>.map(block: (T) -> R): KmmResult<R> =
    if(isFailure) this as KmmResult<R>
    else  KmmResult(block(value!!))

