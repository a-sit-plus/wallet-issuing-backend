import java.io.*
import java.util.zip.Deflater
import java.util.zip.Inflater

class Utils {
    companion object {
        @Throws(IOException::class)
        fun writeBitString(out: OutputStream, ar: BooleanArray) {
            var i = 0
            while (i < ar.size) {
                var b = 0
                for (j in Math.min(i + 7, ar.size - 1) downTo i) {
                    b = b shl 1 or if (ar[j]) 1 else 0
                }
                out.write(b)
                i += 8
            }
        }

        @Throws(IOException::class)
        fun readBitString(`in`: InputStream, ar: BooleanArray) {
            var i = 0
            while (i < ar.size) {
                var b = `in`.read()
                if (b < 0) throw EOFException()
                var j = i
                while (j < i + 8 && j < ar.size) {
                    ar[j] = b and 1 != 0
                    b = b ushr 1
                    j++
                }
                i += 8
            }
        }

        fun ByteArray.zlibCompress(): ByteArray {
            val input = this;

            // Compress the bytes
            // 1 to 4 bytes/char for UTF-8
            val output = ByteArray(input.size * 4)
            val compressor = Deflater().apply {

                setInput(input)
                finish()
            }
            val compressedDataLength: Int = compressor.deflate(output)
            return output.copyOfRange(0, compressedDataLength)
        }

        fun ByteArray.zlibDecompress(): ByteArray {
            val inflater = Inflater()
            val outputStream = ByteArrayOutputStream()

            return outputStream.use {
                val buffer = ByteArray(1024)

                inflater.setInput(this)

                var count = -1
                while (count != 0) {
                    count = inflater.inflate(buffer)
                    outputStream.write(buffer, 0, count)
                }

                inflater.end()
                outputStream.toByteArray()
            }
        }
    }
}