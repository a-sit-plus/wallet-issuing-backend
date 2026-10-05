package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.Extensions.appendPath
import at.asitplus.wallet.backend.Extensions.toResponseEntity
import at.asitplus.wallet.lib.PreparedHttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExtensionsTest {

    @Test
    fun `prepared response adapter preserves status repeated headers and encoded body`() {
        val prepared = PreparedHttpResponse(
            status = HttpStatusCode.Created,
            headers = headers {
                append("Content-Type", "application/json")
                append("X-Value", "first")
                append("X-Value", "second")
            },
            body = "{\"name\":\"Müller\"}",
        )
        val response = prepared.toResponseEntity()
        assertEquals(201, response.statusCode.value())
        assertEquals(listOf("first", "second"), response.headers["X-Value"])
        assertEquals("application/json", response.headers.getFirst("Content-Type"))
        assertEquals(prepared.body, response.body)
    }

    @Test
    fun `appendPath joins trailing base slash with leading path slash once`() {
        assertEquals(
            "https://wallet-issuer.a-sit.plus/transaction/result/abc",
            URI("https://wallet-issuer.a-sit.plus/").toURL().appendPath("/transaction/result/abc")
        )
    }

    @Test
    fun `appendPath preserves configured context path`() {
        assertEquals(
            "https://example.test/issuer/transaction/result/abc",
            URI("https://example.test/issuer/").toURL().appendPath("/transaction/result/abc")
        )
    }
}
