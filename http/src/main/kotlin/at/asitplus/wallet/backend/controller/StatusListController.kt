package at.asitplus.wallet.backend.controller

import at.asitplus.wallet.backend.Extensions.sha256
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.config.CredentialCatalog
import at.asitplus.wallet.backend.config.StatusListGroups
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.ISO_MDOC
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
import org.springframework.web.server.ResponseStatusException
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
    private val statusListGroups: StatusListGroups,
    private val configurationProperties: BackendConfigurationProperties,
) {

    private val statusListJwtType = MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_JWT)
    private val statusListCwtType = MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_CWT)

    /** Lists the status lists of every signing key, since each group publishes under its own path. */
    @GetMapping(Paths.Credentials.Status.CurrentUrl, produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun getStatutsListAggregation(): StatusListAggregation {
        Napier.i("${Paths.Credentials.Status.CurrentUrl} called")
        return StatusListAggregation(
            statusLists = statusListGroups.all.flatMap { it.statusListAgent.provideStatusListAggregation().statusLists }
        ).also {
            Napier.i("${Paths.Credentials.Status.CurrentUrl} returns $it")
        }
    }

    /** Status list of the default signing key, i.e. the path credentials issued before per-credential keys point at. */
    @GetMapping("${Paths.Credentials.StatusUrl}/{timePeriod}")
    fun getStatusList(
        @PathVariable timePeriod: Int,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> = respondWithStatusList(statusListGroups.default.slug, timePeriod, request)

    /** Status list of the signing key configured for one specific credential, see [StatusListGroups]. */
    @GetMapping("${Paths.Credentials.StatusUrl}/{slug}/{timePeriod}")
    fun getStatusList(
        @PathVariable slug: String,
        @PathVariable timePeriod: Int,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        val group = statusListGroups.bySlug(slug)
        if (group == null || group.isDefault) {
            Napier.w("${Paths.Credentials.StatusUrl}/$slug/$timePeriod returns HTTP 404, unknown status list")
            return ResponseEntity.notFound().build()
        }
        return respondWithStatusList(group.slug, timePeriod, request)
    }

    private fun respondWithStatusList(
        slug: String,
        timePeriod: Int,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        val url = Paths.Credentials.StatusUrl + (if (slug.isEmpty()) "" else "/$slug") + "/$timePeriod"
        Napier.i("$url called")
        val accept = Collections.list(request.getHeaders(HttpHeaders.ACCEPT)).joinToString(",")
        val identifier = statusListGroups.bySlug(slug)?.credentialIdentifier
        val representation = CredentialCatalog.entries.singleOrNull { it.identifier == identifier }?.representation
        val defaultType = if (representation == ISO_MDOC) statusListCwtType else statusListJwtType
        val statusListContentType = selectContentType(accept, defaultType)
        val path = with(configurationProperties.revocationList) {
            Path(if (statusListContentType == statusListCwtType) cwtPath(slug) else jwtPath(slug), timePeriod.toString())
        }
        val content = if (path.exists() && path.isReadable()) path.readBytes() else null
        return if (content == null || content.isEmpty()) {
            Napier.w("$url returns HTTP 404")
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
                vary = listOf(HttpHeaders.ACCEPT)
            }

            if (request.matchesNotModified(etag, lastModified)) {
                Napier.d("$url returns HTTP 304")
                ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .headers(headers)
                    .build()
            } else {
                Napier.i("$url returns ${content.count()} chars")
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

    /** RFC 9110, 12.5.1: specific ranges override wildcards, including q=0 exclusions. */
    private fun selectContentType(accept: String, defaultType: MediaType): MediaType {
        if (accept.isBlank()) return defaultType
        val ranges = try {
            MediaType.parseMediaTypes(accept)
        } catch (exception: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Accept header", exception)
        }
        val specificity = compareBy<MediaType> {
            when {
                it.isWildcardType -> 0
                it.isWildcardSubtype -> 1
                else -> 2
            }
        }.thenBy { it.parameters.count { (name, _) -> name != "q" } }
        // The credential-specific default wins an otherwise equal preference.
        val otherType = if (defaultType == statusListJwtType) statusListCwtType else statusListJwtType
        return listOf(defaultType, otherType).mapNotNull { type ->
            val range = ranges.filter { range ->
                range.includes(type) && range.parameters.all { (name, value) ->
                    name == "q" || type.getParameter(name) == value
                }
            }.maxWithOrNull(specificity.thenBy { it.qualityValue })
            range?.takeIf { it.qualityValue > 0 }?.let { type to it }
        }.sortedWith(compareByDescending<Pair<MediaType, MediaType>> { it.second.qualityValue }
            .thenComparator { first, second -> specificity.compare(second.second, first.second) })
            .firstOrNull()?.first
            ?: throw ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Request a JWT or CWT status list")
    }
}
