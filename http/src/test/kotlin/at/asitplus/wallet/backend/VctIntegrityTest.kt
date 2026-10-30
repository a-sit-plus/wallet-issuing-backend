package at.asitplus.wallet.backend

import at.asitplus.signum.indispensable.josef.JwsCompact
import at.asitplus.wallet.backend.config.TypeMetadataIntegrity
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.SD_JWT
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.OpenId4VciClient.RequestOptions
import at.asitplus.wallet.lib.oidvci.OpenId4VciServer
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/** SD-JWT VCs carry the `vct#integrity` of their type metadata document, as Launchpad Testing Playbook v3 requires. */
@SpringBootTest
class VctIntegrityTest {

    @Autowired
    private lateinit var credentialIssuer: OpenId4VciServer

    @Autowired
    private lateinit var authorizationServer: SimpleAuthorizationService

    @Autowired
    private lateinit var typeMetadataIntegrity: TypeMetadataIntegrity

    @Test
    fun `integrity is the W3C SRI sha256 digest`() {
        TypeMetadataIntegrity.of(byteArrayOf()) shouldBe "sha256-47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="
    }

    @Test
    fun `integrity covers the type metadata document`() {
        val document = javaClass.getResourceAsStream("/credential-metadata/eu-pid-sdjwt.json")
            .shouldNotBeNull().use { it.readAllBytes() }

        typeMetadataIntegrity.forVct("urn:eudi:pid:1") shouldBe TypeMetadataIntegrity.of(document)
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun `issued PID carries vct integrity in plain`() = runTest {
        val scheme = AttributeIndex.resolveSdJwtAttributeType("urn:eudi:pid:1").shouldNotBeNull()
        val credential = loadCredential(
            credentialIssuer, authorizationServer, RequestOptions(scheme, SD_JWT),
            vctIntegrity = typeMetadataIntegrity::forVct,
        ).credentials.shouldNotBeNull().first().shouldNotBeNull().credentialString.shouldNotBeNull()
        val payload = Json.parseToJsonElement(
            JwsCompact(credential.substringBefore("~")).plainPayload.decodeToString()
        ).jsonObject

        payload["vct#integrity"].shouldNotBeNull().jsonPrimitive.content shouldBe
                typeMetadataIntegrity.forVct("urn:eudi:pid:1")
    }
}
