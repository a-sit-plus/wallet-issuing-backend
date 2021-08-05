package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.model.IdentifierRegistry
import at.asitplus.wallet.backend.model.IdentifierRepository
import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.agent.Claim
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
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
    fun issuerCredentialDataProvider(): IssuerCredentialDataProvider {
        return object : IssuerCredentialDataProvider {
            override fun getPupilIdCredentials(subjectId: String) = listOf(
                Claim("firstname", "Susanne", "application/text", configurationProperties.credentialLifetime),
                Claim("lastname", "Meier", "application/text", configurationProperties.credentialLifetime),
                Claim("dateOfBirth", "1997-12-26", "application/text", configurationProperties.credentialLifetime),
                Claim("kennzahl", "00200000/00000004", "application/text", configurationProperties.credentialLifetime),
                Claim("schulName", "Quarto Testschule", "application/text", configurationProperties.credentialLifetime),
                Claim(
                    "schulAdresse",
                    "1140 Wien, Breitenseer Straße 13",
                    "application/text",
                    configurationProperties.credentialLifetime
                ),
                Claim("klasse", "3B", "application/text", configurationProperties.credentialLifetime),
                Claim("validUntil", "2021-07-31", "application/text", configurationProperties.credentialLifetime),
            )
        }
    }

    @Bean
    fun issuerAgent(
        @Autowired identifierRegistry: IdentifierRegistry,
        @Autowired issuerCredentialDataProvider: IssuerCredentialDataProvider
    ): Agent {
        return Agent(
            issuerCredentialStore = identifierRegistry,
            dataProvider = issuerCredentialDataProvider,
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