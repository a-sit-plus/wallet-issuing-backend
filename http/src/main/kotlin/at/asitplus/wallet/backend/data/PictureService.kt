package at.asitplus.wallet.backend.data

import io.github.aakira.napier.Napier
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfInt
import org.opencv.imgcodecs.Imgcodecs

/**
 * Optionally compresses and scales pictures used in credentials or as attachments.
 */
class PictureService(
    private val compress: Boolean,
    internal val format: String,
    private val quality: Int,
    private val scale: Boolean,
    private val height: Int,
    private val width: Int,
) {

    init {
        if (format != "webp") throw IllegalArgumentException("format")
        nu.pattern.OpenCV.loadShared()
        System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
    }

    /**
     * Scales the input picture by converting into WebP format with quality 30
     */
    fun scalePicture(input: ByteArray): ByteArray {
        try {
            val matImage = Imgcodecs.imdecode(MatOfByte(*input), Imgcodecs.IMREAD_UNCHANGED)
            val parameters = if (compress) MatOfInt(Imgcodecs.IMWRITE_WEBP_QUALITY, quality) else MatOfInt()
            val output = MatOfByte()
            if (Imgcodecs.imencode(".webp", matImage, output, parameters)) {
                val result = output.toArray()
                Napier.d("scalePicture success: ${input.size} -> ${result.size} bytes")
                return result
            } else {
                Napier.e("scalePicture imencode failed")
                return input
            }
        } catch (it: Throwable) {
            Napier.e("scalePicture failed", it)
            return input
        }
    }

}