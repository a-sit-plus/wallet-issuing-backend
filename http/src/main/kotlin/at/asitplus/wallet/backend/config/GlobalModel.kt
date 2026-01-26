package at.asitplus.wallet.backend.config

import at.asitplus.wallet.backend.Paths
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/** To export some values globally to Thymeleaf templates. */
@ControllerAdvice
class GlobalModel {

    @ModelAttribute("config")
    fun config(props: BackendConfigurationProperties) = props

    @ModelAttribute("paths")
    fun paths() = Paths
}