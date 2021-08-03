package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.MessageWrapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(value = [BackendConfigurationProperties::class])
class BackendConfiguration {

    @Autowired
    private lateinit var configurationProperties: BackendConfigurationProperties

    @Bean
    fun issuerAgent(): Agent {
        return Agent()
    }

    @Bean
    fun issuerMessageWrapper(): MessageWrapper {
        return MessageWrapper(issuerAgent())
    }

    @Bean
    fun issueCredentialMessenger(): IssueCredentialMessenger {
        return IssueCredentialMessenger(
            issuerAgent(),
            issuerMessageWrapper(),
            configurationProperties.publicContext + "/issue",
            false
        )
    }

}