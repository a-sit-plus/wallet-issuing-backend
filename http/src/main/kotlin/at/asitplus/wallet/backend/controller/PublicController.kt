package at.asitplus.wallet.backend.controller

import at.asitplus.wallet.backend.Extensions.sha256
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.lib.agent.Issuer
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.coroutines.runBlocking
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.WebRequest
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.readText
import kotlin.time.toJavaDuration

/**
 * Public endpoints, available without authentication:
 * - Revocation list for Verifiable Credentials (RevocationList2020)
 */
@RestController
class PublicController(
    private val issuer: Issuer,
    private val configurationProperties: BackendConfigurationProperties,
) {

    @GetMapping("/credentials/status/current")
    fun getCurrentVcRevocationLists(): ResponseEntity<List<String>> = runBlocking {
        Napier.i("/credentials/status/current called")
        val rl = issuer.compileCurrentRevocationLists()
        Napier.i("/credentials/status/current returns $rl")
        ResponseEntity.ok(rl)
    }

    @GetMapping("/credentials/status/{timePeriod}")
    fun getVcRevocationList(@PathVariable timePeriod: Int, request: WebRequest): ResponseEntity<String> = runBlocking {
        Napier.i("/credentials/status/$timePeriod called")
        val path = Path(configurationProperties.revocationList.path, timePeriod.toString())
        val content = if (path.exists() && path.isReadable()) path.readText() else null
        if (content.isNullOrEmpty()) {
            Napier.w("/credentials/status/$timePeriod returns HTTP 404")
            return@runBlocking ResponseEntity.notFound().build()
        }
        val etag = content.encodeToByteArray().sha256().encodeToString(Base16()).uppercase()
        val cacheControl =
            CacheControl.maxAge(configurationProperties.revocationList.regularWriteTimeoutDuration.toJavaDuration())
        // Spring (or Tomcat?) appends "-gzip" to the ETag set by us, so we'll need to check that variant too
        if (request.checkNotModified(etag, path.toFile().lastModified())
            || request.checkNotModified("${etag}-gzip", path.toFile().lastModified())
        ) {
            Napier.d("/credentials/status/$timePeriod returns HTTP 304")
            return@runBlocking ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .cacheControl(cacheControl)
                .build()
        }
        Napier.i("/credentials/status/$timePeriod returns ${content.count()} chars")
        return@runBlocking ResponseEntity.ok()
            .cacheControl(cacheControl)
            .body(content)
    }

}