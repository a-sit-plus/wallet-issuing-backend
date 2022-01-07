package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.model.IdentifierRegistry
import at.asitplus.wallet.backend.model.IdentifierRepository
import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.DelegatingProtocolMessenger
import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.agent.MessageWrapper
import at.asitplus.wallet.lib.data.SchemaIndex
import at.asitplus.wallet.lib.encodeBase64
import at.asitplus.wallet.lib.jvm.InMemoryCryptoServiceJvm
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.ResourcePatternResolver
import kotlin.time.ExperimentalTime

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

    @OptIn(ExperimentalTime::class)
    @Bean
    fun issuerCredentialRandomDataProvider(): IssuerCredentialRandomDataProvider {
        val mapOfPhotos =
            resourcePatternResolver.getResources(configurationProperties.randomPhotoLocation.toString() + "/*.jpg")
                .filter { it.exists() }
                .filter { it.filename != null }
                .map { it.filename!! to it.inputStream }
                .map { it.first to it.second.readAllBytes() }
                .map { it.first to it.second.encodeBase64() }
        return IssuerCredentialRandomDataProvider(
            kotlin.time.Duration.minutes(configurationProperties.credentialLifetime.toMinutes()),
            mapOfPhotos.toMap()
        )
    }

    @Bean
    fun issuerCryptoService() = when (configurationProperties.issuerKey.type) {
        KeyType.FILE -> FileCryptoService(configurationProperties.issuerKey.file!!, resourceLoader)
        KeyType.MEMORY -> InMemoryCryptoServiceJvm()
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
    fun issueCredentialMessengerPupilId(
        @Autowired issuerAgent: Agent,
        @Autowired issuerMessageWrapper: MessageWrapper
    ): IssueCredentialMessenger {
        return IssueCredentialMessenger(
            issuerAgent,
            issuerMessageWrapper,
            "${configurationProperties.publicContext}/issue",
            false,
            oobCredentialSchema = SchemaIndex.CRED_PUPIL_ID
        )
    }

    @Bean
    fun issueCredentialMessengerGreenPass(
        @Autowired issuerAgent: Agent,
        @Autowired issuerMessageWrapper: MessageWrapper
    ): IssueCredentialMessenger {
        return IssueCredentialMessenger(
            issuerAgent,
            issuerMessageWrapper,
            "${configurationProperties.publicContext}/issue",
            false,
            oobCredentialSchema = SchemaIndex.CRED_GREEN_PASS
        )
    }

    @Bean
    fun delegatingProtocolMessenger(
        @Autowired issueCredentialMessengerPupilId: IssueCredentialMessenger,
        @Autowired issueCredentialMessengerGreenPass: IssueCredentialMessenger,
    ): DelegatingProtocolMessenger {
        return DelegatingProtocolMessenger(listOf(issueCredentialMessengerPupilId, issueCredentialMessengerGreenPass))
    }

}