package at.asitplus.wallet.backend.data

import com.google.webp.libwebp
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.ScaleMethod
import org.slf4j.LoggerFactory
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString


/**
 * Optionally compresses and scales pictures used in credentials or as attachments.
 */
class WebpPictureService(
    private val compress: Boolean,
    private val quality: Int,
    private val scale: Boolean,
    private val scaleHeight: Int,
    private val scaleWidth: Int,
    libPathJni: String? = null,
    libPathWebp: String? = null,
    libPathSharp: String? = null,
) : PictureService {

    init {
        if (libPathSharp != null) {
            System.load(Path(libPathSharp).absolutePathString())
        } // else webp will load sharpyuv
        if (libPathWebp != null) {
            System.load(Path(libPathWebp).absolutePathString())
        } // else webp_jni will load webp
        if (libPathJni != null) {
            System.load(Path(libPathJni).absolutePathString())
        } else {
            System.loadLibrary("webp_jni")
        }
    }

    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * Scales the input picture by converting into WebP format
     */
    override fun convertPicture(input: ByteArray): ByteArray {
        try {
            val image = ImmutableImage.loader().fromBytes(input).run {
                if (scale) this.scaleTo(scaleWidth, scaleHeight, ScaleMethod.Progressive) else this
            }
            val bytes = toRgb(image)
            val quality = if (compress) quality.toFloat() else 100.0f
            val result = libwebp.WebPEncodeRGB(bytes, image.width, image.height, image.width * 3, quality)
            log.debug("convertPicture: {} -> {} bytes", input.size, result.size)
            return result
        } catch (it: Throwable) {
            log.error("convertPicture failed", it)
            return input
        }
    }

    private fun toRgb(image: ImmutableImage): ByteArray {
        val bytes = ByteArray(image.width * image.height * 3)
        for (p in image.pixels()) {
            val i = 3 * (p.x + p.y * image.width)
            bytes[i + 0] = p.red().toByte()
            bytes[i + 1] = p.green().toByte()
            bytes[i + 2] = p.blue().toByte()
        }
        return bytes
    }

    override val mediaType: String
        get() = "image/webp"
    override val extension: String
        get() = "webp"

}