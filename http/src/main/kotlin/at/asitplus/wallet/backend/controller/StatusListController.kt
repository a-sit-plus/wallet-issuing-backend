package at.asitplus.wallet.backend.controller

import at.asitplus.wallet.backend.Extensions.sha256
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.config.RevocationListConfigurationProperties
import at.asitplus.wallet.lib.agent.StatusListIssuer
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.MediaTypes
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListAggregation
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Path
import java.time.Instant
import java.util.Collections
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
    suspend fun getStatutsListAggregation(): StatusListAggregation {
        Napier.i("${Paths.Credentials.Status.CurrentUrl} called")
        return statusListIssuer.provideStatusListAggregation().also {
            Napier.i("${Paths.Credentials.Status.CurrentUrl} returns $it")
        }
    }

    @GetMapping("${Paths.Credentials.StatusUrl}/{timePeriod}")
    fun getStatusList(
        @PathVariable timePeriod: Int,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        Napier.i("${Paths.Credentials.StatusUrl}/$timePeriod called")
        val acceptMediaTypes = request.getHeader(HttpHeaders.ACCEPT)?.let { MediaType.parseMediaTypes(it) }
        val statusListContentType = acceptMediaTypes.toStatusListContentType()
        val path = configurationProperties.revocationList.getPath(acceptMediaTypes, timePeriod)
        val content = if (path.exists() && path.isReadable()) path.readBytes() else null
        return if (content == null || content.isEmpty()) {
            Napier.w("${Paths.Credentials.StatusUrl}/$timePeriod returns HTTP 404")
            ResponseEntity.notFound().build()
        } else {
            val etag = content.sha256().encodeToString(Base16()).uppercase()
            val cacheControl =
                CacheControl.maxAge(configurationProperties.revocationList.regularWriteTimeoutDuration.toJavaDuration())
            val lastModified = path.toFile().lastModified()
            val headers = HttpHeaders().apply {
                setETag(etag)
                this.cacheControl = cacheControl.headerValue
                this.lastModified = lastModified
                contentType = statusListContentType
            }

            if (request.matchesNotModified(etag, lastModified)) {
                Napier.d("${Paths.Credentials.StatusUrl}/$timePeriod returns HTTP 304")
                ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .headers(headers)
                    .build()
            } else {
                Napier.i("${Paths.Credentials.StatusUrl}/$timePeriod returns ${content.count()} chars")
                ResponseEntity.ok()
                    .headers(headers)
                    .body(content)
            }
        }
    }

    private fun HttpServletRequest.matchesNotModified(etag: String, lastModified: Long): Boolean =
        matchesIfNoneMatch(etag) || matchesIfModifiedSince(lastModified)

    private fun HttpServletRequest.matchesIfNoneMatch(etag: String): Boolean {
        val headerValues = Collections.list(getHeaders(HttpHeaders.IF_NONE_MATCH))
        if (headerValues.isEmpty()) return false

        val acceptedEtags = setOf(
            etag,
            "\"$etag\"",
            "$etag-gzip",
            "\"$etag-gzip\"",
        )
        return headerValues
            .flatMap { it.split(',') }
            .map { it.trim().removePrefix("W/") }
            .any { it == "*" || it in acceptedEtags }
    }

    private fun HttpServletRequest.matchesIfModifiedSince(lastModified: Long): Boolean {
        val ifModifiedSince = getDateHeader(HttpHeaders.IF_MODIFIED_SINCE)
        if (ifModifiedSince < 0) return false

        val lastModifiedSeconds = Instant.ofEpochMilli(lastModified).epochSecond
        val ifModifiedSinceSeconds = Instant.ofEpochMilli(ifModifiedSince).epochSecond
        return lastModifiedSeconds <= ifModifiedSinceSeconds
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
