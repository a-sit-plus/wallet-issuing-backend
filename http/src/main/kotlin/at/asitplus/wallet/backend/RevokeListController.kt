package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.model.IdentifierRegistry
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Collections
import javax.imageio.ImageIO

@Controller
class RevokeListController {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @Autowired
    private lateinit var configurationProperties: BackendConfigurationProperties

    @Autowired
    private lateinit var identifierRegistry: IdentifierRegistry

    @GetMapping("/revoke/list")
    fun revokeList(model: ModelMap): ModelAndView {
        logger.info("/revoke/list called")
        return buildRevokeList(model)
    }

    @GetMapping("/revoke")
    fun revokeByVcId(model: ModelMap, @RequestParam("vcId") vcId: String): ModelAndView {
        logger.info("/revoke called with vcId=$vcId")
        identifierRegistry.revoke(vcId)
        return buildRevokeList(model)
    }

    private fun buildRevokeList(model: ModelMap): ModelAndView {
        model["vcList"] = identifierRegistry.getAllNonRevokedWithDetails()
        model["revocationListUrl"] = "${configurationProperties.publicContext}/credentials/status/1"
        model["revokeActionUrl"] = "${configurationProperties.publicContext}/revoke"
        return ModelAndView("revoke_list", model)
    }

    private fun createQrCodeImage(content: String, size: Int): ByteArray {
        val options = Collections.singletonMap(EncodeHintType.MARGIN, 0)
        val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, options)
        val image = BufferedImage(bits.width, bits.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until bits.height) {
            for (x in 0 until bits.width) {
                image.setRGB(x, y, if (bits[x, y]) 0 else 0xffffff)
            }
        }
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, "png", outputStream)
        return outputStream.toByteArray()
    }
}