package at.asitplus.wallet.backend.controller

import at.asitplus.wallet.backend.Extensions.sha256
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.config.RevocationListConfigurationProperties
import at.asitplus.wallet.lib.agent.StatusListIssuer
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.MediaTypes
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListAggregation
import io.github.aakira.napier.Napier
import io.ktor.http.*
import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.coroutines.runBlocking
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.WebRequest
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.readBytes
import kotlin.time.toJavaDuration


/**
 * Provides status lists for verifiable credentials to the user,
 * implements [Token Status List (TSL)](https://datatracker.ietf.org/doc/draft-ietf-oauth-status-list/).
 */
@RestController
class StatusListController(
    private val statusListIssuer: StatusListIssuer,
    private val configurationProperties: BackendConfigurationProperties,
) {

    private val statusListJwtType = MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_JWT)
    private val statusListCwtType = MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_CWT)

    @GetMapping(Paths.Credentials.Status.CurrentUrl, produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getStatutsListAggregation(): ResponseEntity<StatusListAggregation> = runBlocking {
        Napier.i("${Paths.Credentials.Status.CurrentUrl} called")
        val rl = statusListIssuer.provideStatusListAggregation()
        Napier.i("${Paths.Credentials.Status.CurrentUrl} returns $rl")
        ResponseEntity.ok(rl)
    }

    @GetMapping("${Paths.Credentials.StatusUrl}/{timePeriod}")
    fun getStatusList(
        @PathVariable timePeriod: Int,
        request: WebRequest,
    ): ResponseEntity<*> {
        Napier.i("${Paths.Credentials.StatusUrl}/$timePeriod called")
        val acceptMediaTypes = request.getHeader(HttpHeaders.Accept)?.let { MediaType.parseMediaTypes(it) }
        val contentType = acceptMediaTypes.toStatusListContentType()
        val path = configurationProperties.revocationList.getPath(acceptMediaTypes, timePeriod)
        val content = if (path.exists() && path.isReadable()) path.readBytes() else null
        return if (content == null || content.isEmpty()) {
            Napier.w("${Paths.Credentials.StatusUrl}/$timePeriod returns HTTP 404")
            ResponseEntity.notFound().build<String>()
        } else {
            val etag = content.sha256().encodeToString(Base16()).uppercase()
            val cacheControl =
                CacheControl.maxAge(configurationProperties.revocationList.regularWriteTimeoutDuration.toJavaDuration())
            // Spring (or Tomcat?) appends "-gzip" to the ETag set by us, so we'll need to check that variant too
            val lastModified = path.toFile().lastModified()
            if (request.checkNotModified(etag, lastModified)
                || request.checkNotModified("${etag}-gzip", lastModified)
            ) {
                Napier.d("${Paths.Credentials.StatusUrl}/$timePeriod returns HTTP 304")
                ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(cacheControl)
                    .contentType(contentType)
                    .build<String>()
            } else {
                Napier.i("${Paths.Credentials.StatusUrl}/$timePeriod returns ${content.count()} chars")
                ResponseEntity.ok()
                    .cacheControl(cacheControl)
                    .contentType(contentType)
                    .body(content)
            }
        }
    }

    private fun RevocationListConfigurationProperties.getPath(types: List<MediaType>?, timePeriod: Int): Path =
        if (types.isCompatibleWithCwt())
            Path(cwtPath, timePeriod.toString())
        else
            Path(jwtPath, timePeriod.toString())

    private fun List<MediaType>?.toStatusListContentType(): MediaType =
        if (isCompatibleWithCwt()) statusListCwtType else
            if (isCompatibleWithJwt()) statusListJwtType else
                MediaType.TEXT_PLAIN

    private fun List<MediaType>?.isCompatibleWithCwt() =
        this?.any { it.isCompatibleWith(statusListCwtType) } == true

    private fun List<MediaType>?.isCompatibleWithJwt() =
        this?.any { it.isCompatibleWith(statusListJwtType) } == true

}