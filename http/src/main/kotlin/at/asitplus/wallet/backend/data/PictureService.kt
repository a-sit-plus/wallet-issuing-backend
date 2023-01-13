package at.asitplus.wallet.backend.data


/**
 * Convert pictures used in credentials or attachments.
 */
interface PictureService {

    /**
     * Converts a picture according to implementations, e.g. scale and compress.
     */
    fun convertPicture(input: ByteArray): ByteArray

    /**
     * Media type for picture output, e.g. "image/webp"
     */
    val mediaType: String

    /**
     * Extension for pictures, e.g. "webp"
     */
    val extension: String

}

object NoopPictureService : PictureService {

    override fun convertPicture(input: ByteArray) = input

    override val mediaType: String
        get() = "image/jpg"

    override val extension: String
        get() = "jpg"

}
