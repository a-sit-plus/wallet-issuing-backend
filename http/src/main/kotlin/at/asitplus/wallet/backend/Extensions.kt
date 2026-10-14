package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.PreparedHttpResponse
import at.asitplus.wallet.lib.oauth2.RequestInfo
import io.ktor.http.HttpMethod
import io.ktor.http.headers
import jakarta.servlet.http.HttpServletRequest
import java.net.URL
import java.security.MessageDigest
import org.springframework.http.ResponseEntity
import org.springframework.web.util.UriComponentsBuilder

object Extensions {

    fun URL.appendPath(path: String): String = UriComponentsBuilder.fromUri(toURI()).apply {
        replacePath("${toURI().path.trimEnd('/')}/${path.trimStart('/')}")
    }.toUriString()

    fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

    /** Writes VC-K's encoded body and all header values without further serialization. */
    fun PreparedHttpResponse.toResponseEntity(): ResponseEntity<String> =
        ResponseEntity.status(status.value)
            .headers(org.springframework.http.HttpHeaders().also { target ->
                headers.entries().forEach { (name, values) -> target.addAll(name, values) }
            })
            .body(body)

    fun HttpServletRequest.toRequestInfo() = RequestInfo(
        url = requestURL.toString(),
        method = HttpMethod.parse(method),
        headers = headers {
            headerNames?.toList().orEmpty().forEach { name ->
                getHeaders(name).toList().forEach { value -> append(name, value) }
            }
        },
    )

}
