package at.asitplus.wallet.backend.config

import at.asitplus.openid.OpenIdConstants.Errors
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.lib.ktor.openid.DPoPNonce
import at.asitplus.wallet.lib.oidvci.OAuth2Error
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

    @ExceptionHandler(Exception::class)
    fun handle(throwable: Throwable) = when (throwable) {
        is OAuth2Exception.UseDpopNonce -> ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .header(HttpHeaders.DPoPNonce, throwable.dpopNonce)
            .body(throwable.toOAuth2Error())

        is OAuth2Exception -> ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(throwable.toOAuth2Error())

        else -> ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(OAuth2Error(error = Errors.INVALID_REQUEST))
    }
}