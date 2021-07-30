package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.Agent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BackendConfiguration {

    @Bean
    fun issuerAgent(): Agent {
        return Agent()
    }

}