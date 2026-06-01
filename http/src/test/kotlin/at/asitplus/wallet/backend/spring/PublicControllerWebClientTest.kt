package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.lib.agent.TimePeriodProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import kotlin.time.Clock

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // Doesn't seem to have any effect
    properties = ["server.compression.enabled=true", "server.compression.min-response-size=100B"]
)
@AutoConfigureWebTestClient(timeout = "PT1M")
class PublicControllerWebClientTest {

    @Autowired
    private lateinit var webClient: WebTestClient

    @Autowired
    private lateinit var timePeriodProvider: TimePeriodProvider

    @Test
    fun `GET status list with If-None-Match in second request`() {
        val timePeriod = timePeriodProvider.getRelevantTimePeriods(Clock.System).first()
        val firstResult = webClient.get().uri("${Paths.Credentials.StatusUrl}/$timePeriod")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().exists(HttpHeaders.ETAG)
            .returnResult<String>()

        webClient.get().uri("${Paths.Credentials.StatusUrl}/$timePeriod")
            .header(HttpHeaders.IF_NONE_MATCH, firstResult.responseHeaders[HttpHeaders.ETAG]?.first() ?: "")
            .exchange()
            .expectStatus().isNotModified
            .expectHeader().exists(HttpHeaders.ETAG)
            .returnResult<String>()
    }

    @Test
    fun `GET status list with If-None-Match with gzip suffix in second request`() {
        val timePeriod = timePeriodProvider.getRelevantTimePeriods(Clock.System).first()
        val firstResult = webClient.get().uri("${Paths.Credentials.StatusUrl}/$timePeriod")
            .header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
            .exchange()
            .expectStatus().isOk
            .expectHeader().exists(HttpHeaders.ETAG)
            .returnResult<String>()

        // Server should enable compression and thus add a "-gzip" suffix to our ETag
        // but somehow this does not work in Unit Tests ... so we'll fake it
        // (the ETag itself is enclosed in quotes)
        val etag = firstResult.responseHeaders[HttpHeaders.ETAG]?.first()?.let {
            if (!it.endsWith("-gzip\"")) {
                "${it.removeSuffix("\"")}-gzip\""
            } else {
                it
            }
        }

        webClient.get().uri("${Paths.Credentials.StatusUrl}/$timePeriod")
            .header(HttpHeaders.IF_NONE_MATCH, etag ?: "")
            .exchange()
            .expectStatus().isNotModified
            .expectHeader().exists(HttpHeaders.ETAG)
            .returnResult<String>()
    }
}