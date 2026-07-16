package at.asitplus.wallet.backend

import at.asitplus.catchingUnwrapped
import at.asitplus.signum.indispensable.josef.JsonWebToken
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.wallet.lib.ktor.openid.DPoP
import at.asitplus.wallet.lib.ktor.openid.OAuthClientAttestation
import at.asitplus.wallet.lib.ktor.openid.OAuthClientAttestationPop
import at.asitplus.wallet.lib.oauth2.RequestInfo
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.util.UriComponentsBuilder
import java.net.URL
import java.security.MessageDigest

object Extensions {

    fun URL.appendPath(path: String): String = UriComponentsBuilder.fromUri(toURI()).apply {
        replacePath("${toURI().path.trimEnd('/')}/${path.trimStart('/')}")
    }.toUriString()

    fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

    fun HttpServletRequest.toRequestInfo() = RequestInfo(
        url = requestURL.toString(),
        method = HttpMethod.parse(method),
        dpop = headerToJws(HttpHeaders.DPoP),
        clientAttestation = headerToJws(HttpHeaders.OAuthClientAttestation),
        clientAttestationPop = headerToJws(HttpHeaders.OAuthClientAttestationPop),
    )

    fun HttpServletRequest.headerToJws(headerName: String) =
        catchingUnwrapped { JwsCompactTyped<JsonWebToken>(getHeader(headerName)) }.getOrNull()

}

