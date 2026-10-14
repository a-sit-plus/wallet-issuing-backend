package at.asitplus.wallet.backend.config

import at.asitplus.wallet.lib.agent.StatusListAgent
import at.asitplus.wallet.lib.data.StatusListCwt
import at.asitplus.wallet.lib.data.StatusListJwt
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.RevocationList
import at.asitplus.wallet.lib.data.rfc3986.UniformResourceIdentifier
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class StatusListTokenFetcherTest {
    private val uri = UniformResourceIdentifier("https://example.org/status/1")

    @Test
    fun `JWT and tagged CWT status lists are negotiated and decoded`() = runBlocking<Unit> {
        val pki = MetadataTestPki()
        val jwt = walletStatusToken(pki.keyMaterial, uri.string).value.jws.toString().encodeToByteArray()
        val agent = StatusListAgent(keyMaterial = pki.keyMaterial, statusListBaseUrl = "https://example.org/status")
        val cwt = StatusListCwt(agent.issueStatusListCwt(1, RevocationList.Kind.STATUS_LIST), null).encodeForPublication()
        for ((type, bytes) in listOf("application/statuslist+jwt" to jwt, "application/statuslist+cwt" to cwt,
            "application/statuslist+cwt" to (byteArrayOf(0xd8.toByte(), 0x3d) + cwt))) {
            HttpClient(MockEngine { request ->
                check(request.headers[HttpHeaders.Accept]!!.contains("application/statuslist+jwt"))
                check(request.headers[HttpHeaders.Accept]!!.contains("application/statuslist+cwt"))
                respond(bytes, headers = headersOf(HttpHeaders.ContentType, type))
            }).use { client ->
                val token = StatusListTokenFetcher(client)(uri)
                (token is StatusListJwt) shouldBe (type == "application/statuslist+jwt")
                token.parsedPayload.getOrThrow().subject shouldBe uri
                check(token.resolvedAt != null)
            }
        }
    }

    @Test
    fun `HTTP failures and unsupported media types are rejected`() = runBlocking<Unit> {
        for ((status, type) in listOf(HttpStatusCode.NotFound to "application/statuslist+jwt",
            HttpStatusCode.OK to "application/json")) {
            HttpClient(MockEngine { respond("{}", status, headersOf(HttpHeaders.ContentType, type)) }).use { client ->
                shouldThrow<IllegalArgumentException> { StatusListTokenFetcher(client)(uri) }
            }
        }
    }

    @Test
    fun `insecure status URI is rejected before the request`() = runBlocking<Unit> {
        HttpClient(MockEngine { error("must not fetch") }).use { client ->
            shouldThrow<IllegalArgumentException> {
                StatusListTokenFetcher(client)(UniformResourceIdentifier("http://example.org/status"))
            }
        }
    }
}
