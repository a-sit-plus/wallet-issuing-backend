package at.asitplus.wallet.backend.config

import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.lib.ktor.openid.DPoPNonce
import at.asitplus.wallet.lib.oidvci.OAuth2Exception
import io.ktor.http.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ModelAttribute

/** To export some values globally to Thymeleaf templates. */
@ControllerAdvice
class GlobalModel {

    @ModelAttribute("config")
    fun config(props: BackendConfigurationProperties) = props

    @ModelAttribute("paths")
    fun paths() = Paths

    @ExceptionHandler(value = [OAuth2Exception::class])
    fun handle(oauth2Exception: OAuth2Exception) = when (oauth2Exception) {
        is OAuth2Exception.UseDpopNonce -> ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .header(HttpHeaders.DPoPNonce, oauth2Exception.dpopNonce)
            .body(oauth2Exception.toOAuth2Error())

        is OAuth2Exception -> ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(oauth2Exception.toOAuth2Error())
    }
}
