package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.data.Agent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BackendConfiguration {

    @Bean
    fun issuerAgent(): Agent {
        return Agent()
    }

}