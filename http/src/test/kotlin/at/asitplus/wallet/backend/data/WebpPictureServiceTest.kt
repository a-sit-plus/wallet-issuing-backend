package at.asitplus.wallet.backend.data

import io.kotest.matchers.ints.shouldBeLessThan
import org.junit.jupiter.api.Test
import java.io.File

class WebpPictureServiceTest {

    // 413x531 pixels, 101.028 bytes
    private val input = File("src/test/resources/portrait.jpeg").readBytes()

    @Test
    fun compress() {
        val pictureService = WebpPictureService(true, 30, false, 0, 0)

        val output = pictureService.convertPicture(input)
        // 101028 -> 7800 bytes
        // File("outputCompress.webp").writeBytes(output)

        output.size shouldBeLessThan input.size
    }

    @Test
    fun compressAndScale() {
        val pictureService = WebpPictureService(true, 30, true, 154, 120)

        val output = pictureService.convertPicture(input)
        // 101028 -> 1382 bytes
        // File("outputCompressAndScale.webp").writeBytes(output)

        output.size shouldBeLessThan input.size
    }

    @Test
    fun scale() {
        val pictureService = WebpPictureService(false, 30, true, 154, 120)

        val output = pictureService.convertPicture(input)
        // 101028 -> 10452 bytes
        // File("outputScale.webp").writeBytes(output)

        output.size shouldBeLessThan input.size
    }
}