package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.Base64
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.NextMessage
import at.asitplus.wallet.lib.encodeBase64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.runBlocking
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
class InitializationController {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Autowired
    private lateinit var configurationProperties: BackendConfigurationProperties

    @Autowired
    private lateinit var issueCredentialMessengerPupilId: IssueCredentialMessenger

    @Autowired
    private lateinit var issueCredentialMessengerGreenPass: IssueCredentialMessenger

    @GetMapping("/initialize")
    fun demo(model: ModelMap): ModelAndView {
        log.info("/initialize called")
        val size = 400
        runBlocking {
            val oobPupilId = issueCredentialMessengerPupilId.startCreatingInvitation()
            if (oobPupilId is NextMessage.Send) {
                val content =
                    "${configurationProperties.publicContext}/invite/wallet?oob=${oobPupilId.message.encodeToByteArray().encodeBase64(Base64.UrlSafeNoPadding)}"
                model["qrcodePupilId"] = createQrCodeImage(content, size).encodeBase64()
            } else {
                model["error"] = "Wrong internal state"
            }
            val oobGreenPass = issueCredentialMessengerGreenPass.startCreatingInvitation()
            if (oobGreenPass is NextMessage.Send) {
                val content =
                    "${configurationProperties.publicContext}/invite/wallet?oob=${oobGreenPass.message.encodeToByteArray().encodeBase64(Base64.UrlSafeNoPadding)}"
                model["qrcodeGreenPass"] = createQrCodeImage(content, size).encodeBase64()
            } else {
                model["error"] = "Wrong internal state"
            }
            model["qrcodewidth"] = size
        }
        return ModelAndView("initialize", model)
    }

    @GetMapping("/invite/wallet")
    fun invite(model: ModelMap, @RequestParam(name = "oob", required = false) oob: String): ModelAndView {
        log.info("/invite/wallet?oob=$oob called")
        val content = "${configurationProperties.publicContext}/invite/wallet?oob=${oob}"
        val size = 400
        model["qrcodewidth"] = size
        model["qrcode"] = createQrCodeImage(content, size).encodeBase64()
        return ModelAndView("invite_wallet", model)
    }

    @GetMapping("/invite/verify")
    fun present(model: ModelMap, @RequestParam(name = "oob", required = false) oob: String): ModelAndView {
        log.info("/invite/verify?oob=$oob called")
        val content = "${configurationProperties.publicContext}/invite/verify?oob=${oob}"
        val size = 400
        model["qrcodewidth"] = size
        model["qrcode"] = createQrCodeImage(content, size).encodeBase64()
        return ModelAndView("invite_verify", model)
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