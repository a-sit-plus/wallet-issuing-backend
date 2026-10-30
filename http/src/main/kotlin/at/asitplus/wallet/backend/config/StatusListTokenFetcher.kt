package at.asitplus.wallet.backend.config

import at.asitplus.signum.indispensable.cosef.CoseSigned
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.wallet.lib.agent.TrustedCertificates
import at.asitplus.wallet.lib.agent.requireTrustedSigningCertificate
import at.asitplus.wallet.lib.agent.validation.StatusListTokenResolver
import at.asitplus.wallet.lib.data.StatusListCwt
import at.asitplus.wallet.lib.data.StatusListJwt
import at.asitplus.wallet.lib.data.StatusListToken
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.MediaTypes
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListTokenPayload
import at.asitplus.wallet.lib.data.rfc3986.UniformResourceIdentifier
import at.asitplus.wallet.lib.validation.CheckOutcome
import at.asitplus.wallet.lib.validation.SignatureCheck
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.decodeFromByteArray
import kotlin.time.Clock

/** Shared JWT/CWT retrieval, usable by wallet attestations now and credential validators later. */
class StatusListTokenFetcher(
    private val httpClient: HttpClient = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 10_000 }
    },
    private val clock: Clock = Clock.System,
) : StatusListTokenResolver, AutoCloseable {
    override fun close() = httpClient.close()

    override suspend fun invoke(statusListUrl: UniformResourceIdentifier): StatusListToken {
        require(java.net.URI(statusListUrl.string).let { it.scheme == "https" && it.host != null }) { "Status list URL must use HTTPS" }
        val response = httpClient.get(statusListUrl.string) {
            header(HttpHeaders.Accept, "${MediaTypes.Application.STATUSLIST_JWT}, ${MediaTypes.Application.STATUSLIST_CWT};q=0.9")
        }
        require(response.status.isSuccess()) { "Status list endpoint returned ${response.status}" }
        val bytes = response.body<ByteArray>()
        return when (response.contentType()?.withoutParameters()?.toString()?.lowercase()) {
            MediaTypes.Application.STATUSLIST_JWT -> StatusListJwt(
                JwsCompactTyped<StatusListTokenPayload>(bytes.decodeToString().trim()), clock.now(),
            )
            MediaTypes.Application.STATUSLIST_CWT, MediaTypes.Application.IDENTIFIERLIST_CWT -> {
                // RFC 8392 permits CWT tag 61 around the COSE_Sign1 tag.
                val cose = if (bytes.size > 2 && bytes[0] == 0xd8.toByte() && bytes[1] == 0x3d.toByte()) bytes.copyOfRange(2, bytes.size) else bytes
                StatusListCwt(coseCompliantSerializer.decodeFromByteArray<CoseSigned<ByteArray>>(cose), clock.now())
            }
            else -> throw IllegalArgumentException("Unexpected status list Content-Type: ${response.contentType()}")
        }
    }
}

/**
 * Enforces wallet-profile status-signer trust before compatibility APIs can use their integrity-only status policy.
 * URI binding, freshness, index lookup and status acceptance are still evaluated by VC-K's StatusValidator.
 */
internal class TrustedWalletStatusListResolver(
    private val fetch: StatusListTokenResolver,
    private val anchors: TrustedCertificates,
    private val clock: Clock = Clock.System,
) : StatusListTokenResolver {
    override suspend fun invoke(statusListUrl: UniformResourceIdentifier): StatusListToken {
        val token = fetch(statusListUrl)
        val signature = when (token) {
            is StatusListJwt -> SignatureCheck().verify(token.value.jws)
            is StatusListCwt -> SignatureCheck().verify(token.value)
        }
        (signature.outcome as? CheckOutcome.Failed)?.let { throw it.throwable }
        require(signature.outcome == CheckOutcome.Passed) { "Status list signature could not be verified" }
        // LoTEs may list the status signer itself as revocation service identity, which then is its own x5c.
        signature.certificateChain.requireTrustedSigningCertificate(anchors, clock.now())
        return token
    }
}
