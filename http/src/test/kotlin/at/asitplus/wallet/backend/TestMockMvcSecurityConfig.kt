package at.asitplus.wallet.backend

import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers

/**
 * Restores Spring Security MockMvc integration that was auto-configured in Spring Boot 3
 * but must be registered explicitly in Spring Boot 4.
 */
@Configuration
class TestMockMvcSecurityConfig {

    @Bean
    fun securityMockMvcBuilderCustomizer(): MockMvcBuilderCustomizer =
        MockMvcBuilderCustomizer { it.apply(SecurityMockMvcConfigurers.springSecurity()) }

}
