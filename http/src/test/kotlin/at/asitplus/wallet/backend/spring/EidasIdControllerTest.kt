package at.asitplus.wallet.backend.spring

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.util.Base64Utils
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO


@SpringBootTest(properties =  ["backend.authn.device-binding.attestation.noop=true"])
@AutoConfigureMockMvc
@ActiveProfiles("eidasid")
class EidasIdControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun demo_unauthenticated() {
        mockMvc.get("/eidasid/initialize")
            .andExpect { status { is3xxRedirection() } }
            .andReturn()
    }

    @Test
    fun demo_success() {
        val result = mockMvc.get("/eidasid/initialize") {
            with(oidcLogin().idToken {
                it.claim("sub", "bar")
                    .claim("birthdate", "2020-01-01")
                    .claim("given_name", "Susanne")
                    .claim("family_name", "Meier")
            }.authorities(SimpleGrantedAuthority("EIDASID")))
        }.andExpect { status { isOk() } }
            .andReturn()

        val nonceUrl = parseResponse(result, "qrcode")
        nonceUrl shouldContain "#nonce="
    }

    private fun parseResponse(result: MvcResult, attributeName: String): String {
        val qrCodeEncoded = result.modelAndView!!.model[attributeName].toString()
        val response = Base64Utils.decodeFromString(qrCodeEncoded)
        val image = ImageIO.read(ByteArrayInputStream(response))
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val source = RGBLuminanceSource(image.width, image.height, pixels)
        val bm = BinaryBitmap(HybridBinarizer(source))
        return QRCodeReader().decode(bm).text
    }

}