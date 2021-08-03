package at.asitplus.wallet.backend

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.util.Base64Utils
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

@SpringBootTest
@AutoConfigureMockMvc
internal class DemoControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun demo_success() {
        val result = mockMvc.get("/demo")
            .andExpect {
                status { isOk() }
            }.andReturn()

        val oobInvitationUrl = parseResponse(result)

        assertTrue(oobInvitationUrl.contains("?oob="))
    }

    private fun parseResponse(result: MvcResult): String {
        val qrCodeEncoded = result.modelAndView!!.model["qrcode"].toString()
        val response = Base64Utils.decodeFromString(qrCodeEncoded)
        val image = ImageIO.read(ByteArrayInputStream(response))
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val source = RGBLuminanceSource(image.width, image.height, pixels)
        val bm = BinaryBitmap(HybridBinarizer(source))
        return QRCodeReader().decode(bm).text
    }

}