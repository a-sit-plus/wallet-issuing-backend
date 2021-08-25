package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.model.IdentifierRegistry
import at.asitplus.wallet.backend.model.IdentifierRepository
import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.InMemoryCryptoService
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.agent.MessageWrapper
import at.asitplus.wallet.lib.toBase64
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.ResourcePatternResolver

@Configuration
@EnableConfigurationProperties(value = [BackendConfigurationProperties::class])
class BackendConfiguration {

    @Autowired
    private lateinit var configurationProperties: BackendConfigurationProperties

    @Autowired
    private lateinit var resourceLoader: ResourceLoader

    @Autowired
    private lateinit var resourcePatternResolver: ResourcePatternResolver

    @Bean
    fun identifierRegistry(@Autowired identifierRepository: IdentifierRepository): IdentifierRegistry {
        return IdentifierRegistry(identifierRepository)
    }

    @Bean
    fun issuerCredentialRandomDataProvider(): IssuerCredentialRandomDataProvider {
        val listOfPhotos = resourcePatternResolver.getResources(configurationProperties.randomPhotoLocation.toString() + "/*.jpg").filter { it.exists() }.map { it.inputStream }.map { it.readAllBytes() }.map { it.toBase64() }
        return IssuerCredentialRandomDataProvider(
            configurationProperties.credentialLifetime,
            listOfPhotos
        )
    }

    @Bean
    fun issuerCryptoService() = when (configurationProperties.issuerKey.type) {
        KeyType.FILE -> FileCryptoService(configurationProperties.issuerKey.file!!, resourceLoader)
        KeyType.MEMORY -> InMemoryCryptoService()
    }

    @Bean
    fun issuerAgent(
        @Autowired identifierRegistry: IdentifierRegistry,
        @Autowired issuerCredentialRandomDataProvider: IssuerCredentialDataProvider,
        @Autowired issuerCryptoService: CryptoService
    ): Agent {
        return Agent(
            cryptoService = issuerCryptoService,
            issuerCredentialStore = identifierRegistry,
            dataProvider = issuerCredentialRandomDataProvider,
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
            "${configurationProperties.publicContext}/issue",
            false
        )
    }

}