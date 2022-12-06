package at.asitplus.wallet.backend.data

import io.kotest.matchers.ints.shouldBeLessThan
import io.matthewnelson.component.base64.decodeBase64ToArray
import org.junit.jupiter.api.Test

class WebpAdapterTest {
    @Test
    fun testFallbackPhoto() {
        val pictureService = PictureService(true, "webp", 30, false, 0, 0)
        val input = RandomCredentialDataProvider.fallbackPhoto.decodeBase64ToArray()!!

        val output = pictureService.scalePicture(input)

        output.size shouldBeLessThan input.size
    }
}