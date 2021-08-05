package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.model.IdentifierRegistry
import at.asitplus.wallet.backend.model.IdentifierRepository
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.util.Base64Utils
import java.io.ByteArrayInputStream
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.assertContains
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
internal class DemoControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var identifierRegistry: IdentifierRegistry

    @Autowired
    private lateinit var identifierRepository: IdentifierRepository

    @BeforeEach
    fun beforeEach() {
        identifierRepository.deleteAll()
    }

    @Test
    fun demo_success() {
        val result = mockMvc.get("/demo")
            .andExpect { status { isOk() } }
            .andReturn()

        val oobInvitationUrl = parseResponse(result)

        assertContains(oobInvitationUrl, "?oob=")
    }

    @Test
    fun `revokeList contains issued credentials`() {
        val vcId = UUID.randomUUID().toString()

        identifierRegistry.storeGetNextIndex(vcId)

        val result = mockMvc.get("/revoke/list")
            .andExpect { status { isOk() } }
            .andReturn()

        assertNotNull(result.modelAndView)
        val vcList = result.modelAndView!!.model["vcList"]
        assertIs<Collection<String>>(vcList)
        assertContains(vcList, vcId)
    }

    @Test
    fun `revokeList should not contain revoked entries`() {
        val vcId = UUID.randomUUID().toString()

        identifierRegistry.storeGetNextIndex(vcId)
        identifierRegistry.revoke(vcId)

        val result = mockMvc.get("/revoke/list")
            .andExpect { status { isOk() } }
            .andReturn()

        assertNotNull(result.modelAndView)
        val vcList = result.modelAndView!!.model["vcList"]
        assertIs<Collection<String>>(vcList)
        assertTrue(vcList.isEmpty())
    }

    @Test
    fun revokeList_revoke_success() {
        val vcId = UUID.randomUUID().toString()

        identifierRegistry.storeGetNextIndex(vcId)

        val result = mockMvc.get("/revoke/list")
            .andExpect {
                status { isOk() }
            }.andReturn()

        assertNotNull(result.modelAndView)
        val revokeActionUrl = "${result.modelAndView!!.model["revokeActionUrl"]}?key=$vcId"
        mockMvc.get(revokeActionUrl).andExpect { status { isOk() } }

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