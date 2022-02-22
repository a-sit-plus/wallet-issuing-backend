package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.lib.data.AtomicAttributeCredential
import at.asitplus.wallet.lib.encodeBase64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.random.Random

@Controller
class DebugController(
    private val extNonceAuthnService: ExtNonceAuthnService,
    private val configurationProperties: BackendConfigurationProperties,
    private val pupilIdRevocationService: PupilIdRevocationService,
    private val credentialRepo: IssuedCredentialRepository,
    private val deviceBindingRepo: DeviceBindingRepository,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * Displays a QR code to scan with the Wallet App to get a nonce for authn during the device binding process
     */
    @GetMapping("/debug/initialize")
    fun initialize(model: ModelMap): ModelAndView {
        if (!configurationProperties.debug.enabled) return ModelAndView("index", model)
        log.info("/debug/initialize called")
        runBlocking {
            val nonce = extNonceAuthnService.generateNonce()
            val content = "${configurationProperties.publicContext}/help/wallet?nonce=${nonce}"
            val qrCodeImage = createQrCodeImage(content, configurationProperties.debug.qrCodeSize)
            model["qrcode"] = qrCodeImage.encodeBase64()
            model["qrcodeWidth"] = configurationProperties.debug.qrCodeSize
        }
        return ModelAndView("initialize", model)
    }

    @GetMapping("/debug/nonce")
    fun getNonce(): ResponseEntity<String> {
        if (!configurationProperties.debug.enabled) return ResponseEntity.ok("")
        log.info("/debug/nonce called")
        return ResponseEntity.ok(extNonceAuthnService.generateNonce())
    }

    /**
     * Display help page if user scans QR code from [initialize]
     */
    @GetMapping("/help/wallet")
    fun helpWallet(model: ModelMap, @RequestParam(name = "nonce", required = false) nonce: String): ModelAndView {
        log.info("/help/wallet?nonce=$nonce called")
        val content = "${configurationProperties.publicContext}/help/wallet?nonce=${nonce}"
        val qrCodeImage = createQrCodeImage(content, configurationProperties.debug.qrCodeSize)
        model["qrcode"] = qrCodeImage.encodeBase64()
        model["qrcodeWidth"] = configurationProperties.debug.qrCodeSize
        return ModelAndView("help_wallet", model)
    }

    /**
     * Display help page if user scans QR code presented in Wallet App for verification
     */
    @GetMapping("/invite/verify")
    fun inviteVerify(model: ModelMap, @RequestParam(name = "oob", required = false) oob: String): ModelAndView {
        log.info("/invite/verify?oob=$oob called")
        val content = "${configurationProperties.publicContext}/invite/verify?oob=${oob}"
        model["qrcodeWidth"] = configurationProperties.debug.qrCodeSize
        model["qrcode"] = createQrCodeImage(content, configurationProperties.debug.qrCodeSize).encodeBase64()
        return ModelAndView("invite_verify", model)
    }

    @GetMapping("/debug/credential/list")
    fun revokeList(model: ModelMap): ModelAndView {
        if (!configurationProperties.debug.enabled) return ModelAndView("index", model)
        log.info("/debug/credential/list called")
        return buildCredentialList(model)
    }

    @GetMapping("/debug/credential/revoke")
    fun revokeByVcId(model: ModelMap, @RequestParam("vcId") vcId: String): ModelAndView {
        if (!configurationProperties.debug.enabled) return ModelAndView("index", model)
        log.info("/debug/credential/revoke called with vcId=$vcId")
        pupilIdRevocationService.revokeCredentialsByVcId(vcId)
        return ModelAndView("redirect:/debug/credential/list")
    }

    @GetMapping("/debug/credential/create")
    fun createCredential(model: ModelMap): ModelAndView {
        if (!configurationProperties.debug.enabled) return ModelAndView("index", model)
        log.info("/debug/credential/create called")
        val attributeName = UUID.randomUUID().toString()
        val attributeValue = UUID.randomUUID().toString()
        val credentialSubject = AtomicAttributeCredential(UUID.randomUUID().toString(), attributeName, attributeValue)
        val exp = java.time.Instant.now().plusSeconds(3600)
        val deviceBinding = DeviceBinding("bpk", Random.Default.nextBytes(32), "deviceName", "deviceId").also {
            deviceBindingRepo.save(it)
        }
        IssuedCredential(UUID.randomUUID().toString(), credentialSubject.id, exp, deviceBinding, attributeName).also {
            credentialRepo.save(it)
        }
        return ModelAndView("redirect:/debug/credential/list")
    }

    private fun buildCredentialList(model: ModelMap): ModelAndView {
        val vcList = pupilIdRevocationService.getAllNonRevokedWithDetails().map {
            CredentialListDto(it.vcId, it.createdOn.toString(), it.attributeName, it.subjectId)
        }
        model["vcList"] = vcList
        model["createCredentialUrl"] = "${configurationProperties.publicContext}/debug/credential/create"
        model["revokeActionUrl"] = "${configurationProperties.publicContext}/debug/credential/revoke"
        return ModelAndView("credential_list", model)
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


/**
 * Used in "credential_list.html"
 */
data class CredentialListDto(
    val vcId: String,
    val issuanceDate: String,
    val attributeName: String,
    val subjectId: String
)