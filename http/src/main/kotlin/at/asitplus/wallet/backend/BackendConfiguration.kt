package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.model.IdentifierRegistry
import at.asitplus.wallet.backend.model.IdentifierRepository
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
    fun identifierRegistry(@Autowired identifierRepository: IdentifierRepository): IdentifierRegistry {
        return IdentifierRegistry(identifierRepository)
    }

    @Bean
    fun issuerAgent(@Autowired identifierRegistry: IdentifierRegistry): Agent {
        return Agent(
            issuerCredentialStore = identifierRegistry,
            revocationListUrl = "${configurationProperties.publicContext}/credentials/status/1"
        )
    }

    @Bean
    fun issuerMessageWrapper(@Autowired issuerAgent: Agent): MessageWrapper {
        return MessageWrapper(issuerAgent.cryptoService)
    }

    @Bean
    fun issueCredentialMessenger(
        @Autowired issuerAgent: Agent,
        @Autowired issuerMessageWrapper: MessageWrapper
    ): IssueCredentialMessenger {
        return IssueCredentialMessenger(
            issuerAgent,
            issuerMessageWrapper,
            configurationProperties.publicContext + "/issue",
            false
        )
    }

}