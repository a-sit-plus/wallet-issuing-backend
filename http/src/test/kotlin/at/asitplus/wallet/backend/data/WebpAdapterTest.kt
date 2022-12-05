package at.asitplus.wallet.backend.data

import io.kotest.matchers.ints.shouldBeLessThan
import io.matthewnelson.component.base64.decodeBase64ToArray
import org.junit.jupiter.api.Test

class WebpAdapterTest {
    @Test
    fun testFallbackPhoto() {
        val input = RandomCredentialDataProvider(mapOf()).fallbackPhoto.decodeBase64ToArray()!!

        val output = WebpAdapter.scalePicture(input)

        output.size shouldBeLessThan input.size
    }
}