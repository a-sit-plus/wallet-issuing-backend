package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.Extensions.appendPath
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.lib.agent.TimePeriodProvider
import at.asitplus.wallet.lib.data.AtomicAttributeCredential
import at.asitplus.wallet.lib.encodeBase64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.util.UriComponentsBuilder
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours

@Controller
class DebugController(
    private val extNonceAuthnService: ExtNonceAuthnService,
    private val configurationProperties: BackendConfigurationProperties,
    private val revocationService: RevocationService,
    private val credentialRepo: IssuedCredentialRepository,
    private val deviceBindingRepo: DeviceBindingRepository,
    private val clock: Clock,
    private val timePeriodProvider: TimePeriodProvider,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * Displays a QR code to scan with the Wallet App to get a nonce for authn during the device binding process
     */
    @GetMapping("/debug/initialize")
    fun initialize(model: ModelMap): ModelAndView {
        if (!configurationProperties.debug.enabled) return ModelAndView("index", model)
        log.info("/debug/initialize called")
        val nonceBpk = extNonceAuthnService.generateNonce()
        if (nonceBpk == null) {
            model["error"] = "Internal error: Could not generate nonce"
            return ModelAndView("initialize", model)
        }
        val content = UriComponentsBuilder.fromHttpUrl(configurationProperties.publicContext)
            .pathSegment("help", "wallet")
            .fragment("nonce=${nonceBpk.nonce}&server=${configurationProperties.publicContext}")
            .toUriString()
        val qrCodeImage = createQrCodeImage(content, configurationProperties.debug.qrCodeSize)
        model["qrcode"] = qrCodeImage.encodeBase64()
        model["qrcodeWidth"] = configurationProperties.debug.qrCodeSize
        model["creation"] = clock.now().toString()
        return ModelAndView("initialize", model)
    }

    @GetMapping("/debug/nonce")
    fun getNonce(): ResponseEntity<String> {
        if (!configurationProperties.debug.enabled) return ResponseEntity.notFound().build()
        val nonce = extNonceAuthnService.generateNonce()?.nonce
        log.info("/debug/nonce returns '{}'", nonce)
        return ResponseEntity.ok(nonce)
    }

    /**
     * Display help page if user scans QR code from [initialize]
     */
    @GetMapping("/help/wallet")
    fun helpWallet(model: ModelMap): ModelAndView {
        log.info("/help/wallet called")
        return ModelAndView("help_wallet", model)
    }

    /**
     * Display help page if user scans QR code presented in Wallet App for verification
     */
    @GetMapping("/help/verify")
    fun inviteVerify(model: ModelMap): ModelAndView {
        log.info("/help/verify called")
        return ModelAndView("help_verify", model)
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
        log.info("/debug/credential/revoke called with vcId='{}'", vcId)
        revocationService.revokeCredentialsByVcId(
            vcId,
            timePeriodProvider.getTimePeriodFor(clock.now())
        )
        return ModelAndView("redirect:/debug/credential/list")
    }

    @GetMapping("/debug/credential/create")
    fun createCredential(model: ModelMap): ModelAndView {
        if (!configurationProperties.debug.enabled) return ModelAndView("index", model)
        log.info("/debug/credential/create called")
        val attributeName = UUID.randomUUID().toString()
        val attributeValue = UUID.randomUUID().toString()
        val credentialSubject =
            AtomicAttributeCredential(UUID.randomUUID().toString(), attributeName, attributeValue)
        val exp = clock.now() + 1.hours
        val deviceName = "fake-" + UUID.randomUUID().toString()
        val deviceId = UUID.randomUUID().toString()
        val bpk = UUID.randomUUID().toString()
        val deviceBinding = DeviceBinding(
            bpk,
            Random.Default.nextBytes(32),
            deviceName,
            deviceId,
            exp.toJavaInstant()
        )
            .also { deviceBindingRepo.save(it) }
        val vcId = UUID.randomUUID().toString()
        val revocationListIndex = (credentialRepo.getMaxRevocationListIndex() ?: 0) + 1
        IssuedCredential(
            vcId,
            credentialSubject.id,
            exp.toJavaInstant(),
            timePeriodProvider.getTimePeriodFor(clock.now()),
            deviceBinding,
            attributeName,
            revocationListIndex
        )
            .also { credentialRepo.save(it) }
        return ModelAndView("redirect:/debug/credential/list")
    }

    private fun buildCredentialList(model: ModelMap): ModelAndView {
        val vcList = revocationService.getAllNonRevokedWithDetails().map {
            CredentialListDto(
                it.vcId,
                it.createdOn.toString(),
                it.attributeName,
                it.subjectId,
                it.deviceBinding.deviceName,
                it.deviceBinding.bpk
            )
        }
        model["vcList"] = vcList
        model["createCredentialUrl"] = appendPath(
            configurationProperties.publicContext,
            "debug", "credential", "create"
        )
        model["revokeActionUrl"] = appendPath(
            configurationProperties.publicContext,
            "debug", "credential", "revoke"
        )
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
    val subjectId: String,
    val deviceName: String,
    val bpk: String,
)