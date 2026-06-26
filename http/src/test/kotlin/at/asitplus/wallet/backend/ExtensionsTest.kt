package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.Extensions.appendPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI

class ExtensionsTest {

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
