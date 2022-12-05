package at.asitplus.wallet.backend.data

import org.opencv.core.MatOfByte
import org.opencv.core.MatOfInt
import org.opencv.imgcodecs.Imgcodecs
import org.slf4j.LoggerFactory

/**
 * Uses OpenCV packaged with Java bindings to write WebP images
 *
 * Uses library from https://github.com/openpnp/opencv
 */
object WebpAdapter {

    init {
        nu.pattern.OpenCV.loadShared()
        System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
    }

    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * Scales the input picture by converting into WebP format with quality 30
     */
    fun scalePicture(input: ByteArray): ByteArray {
        try {
            val matImage = Imgcodecs.imdecode(MatOfByte(*input), Imgcodecs.IMREAD_UNCHANGED)
            val parameters = MatOfInt(Imgcodecs.IMWRITE_WEBP_QUALITY, 30)
            val output = MatOfByte()
            if (Imgcodecs.imencode(".webp", matImage, output, parameters)) {
                val result = output.toArray()
                log.debug("scalePicture success: {} -> {} bytes", input.size, result.size)
                return result
            } else {
                log.error("scalePicture imencode failed")
                return input
            }
        } catch (it: Throwable) {
            log.error("scalePicture failed", it)
            return input
        }
    }

}