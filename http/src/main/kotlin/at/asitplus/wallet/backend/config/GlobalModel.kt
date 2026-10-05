package at.asitplus.wallet.backend.config

import at.asitplus.wallet.backend.Extensions.toResponseEntity
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.lib.oauth2.toHttpResponse
import at.asitplus.wallet.lib.oauth2.toResourceServerHttpResponse
import at.asitplus.wallet.lib.oidvci.OAuth2Exception
import io.ktor.http.*
import jakarta.servlet.http.HttpServletRequest
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
    fun handle(oauth2Exception: OAuth2Exception, request: HttpServletRequest) =
        if (request.requestURI.removePrefix(request.contextPath) == Paths.CredentialUrl) {
            oauth2Exception.toResourceServerHttpResponse(request.getHeader(HttpHeaders.Authorization))
        } else {
            oauth2Exception.toHttpResponse()
        }.toResponseEntity()
}
