package at.asitplus.wallet.backend

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class OpenApiConfiguration {
    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .components(
                Components().addSecuritySchemes(
                    "extNonce",
                    SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .name("X-Auth-ExtNonce")
                        .`in`(SecurityScheme.In.HEADER)
                        .description("Nonce to be scanned from QR Code created by ECO, to be transmitted in the header `X-Auth-ExtNonce`.")
                ).addSecuritySchemes(
                    "xAuthToken",
                    SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .name("X-Auth-Token")
                        .`in`(SecurityScheme.In.HEADER)
                        .description("Session identifier from the previous response, contained in the header `X-Auth-Token`.")
                ).addSecuritySchemes(
                    "apiKey",
                    SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .name("X-API—Key")
                        .`in`(SecurityScheme.In.HEADER)
                        .description("API Key to be used for calls to the revocation controller, contained in header `X-API-Key`.")
                ).addSecuritySchemes(
                    "deviceBinding",
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Response to challenge sent in header `WWW—Authenticate`, shall contain JWT signed with device binding key.")
                )
            )
            .info(Info().title("PupilId API").description("PupilId Backend Service"))
    }
}