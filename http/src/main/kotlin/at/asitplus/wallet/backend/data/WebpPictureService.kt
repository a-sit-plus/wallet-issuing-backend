package at.asitplus.wallet.backend.data

import org.opencv.core.MatOfByte
import org.opencv.core.MatOfInt
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.Imgproc.INTER_AREA
import org.slf4j.LoggerFactory


/**
 * Optionally compresses and scales pictures used in credentials or as attachments.
 */
class WebpPictureService(
    private val compress: Boolean,
    private val quality: Int,
    private val scale: Boolean,
    private val height: Int,
    private val width: Int,
) : PictureService {

    init {
        nu.pattern.OpenCV.loadShared()
        System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME)
    }

    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * Scales the input picture by converting into WebP format with quality 30
     */
    override fun convertPicture(input: ByteArray): ByteArray {
        try {
            val inputImage = Imgcodecs.imdecode(MatOfByte(*input), Imgcodecs.IMREAD_UNCHANGED)
            if (scale) {
                val scaleSize = Size(width.toDouble(), height.toDouble())
                Imgproc.resize(inputImage, inputImage, scaleSize, 0.0, 0.0, INTER_AREA)
            }
            val resultMob = MatOfByte()
            val parameters = if (compress) MatOfInt(Imgcodecs.IMWRITE_WEBP_QUALITY, quality) else MatOfInt()
            if (Imgcodecs.imencode(".webp", inputImage, resultMob, parameters)) {
                log.debug("convertPicture: {} -> {} bytes", input.size, resultMob.toArray().size)
                return resultMob.toArray()
            } else {
                log.error("convertPicture: imencode failed")
                return input
            }
        } catch (it: Throwable) {
            log.error("convertPicture failed", it)
            return input
        }
    }

    override val mediaType: String
        get() = "image/webp"
    override val extension: String
        get() = "webp"

}