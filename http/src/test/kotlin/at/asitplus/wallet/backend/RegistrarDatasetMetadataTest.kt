package at.asitplus.wallet.backend

import at.asitplus.openid.IssuerInfo
import at.asitplus.openid.IssuerMetadata
import at.asitplus.openid.OpenIdConstants
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.wallet.backend.config.MetadataTestPki
import at.asitplus.wallet.lib.agent.TrustedCertificates
import at.asitplus.wallet.lib.jws.SignJwt
import at.asitplus.wallet.lib.validation.*
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.security.KeyStore
import kotlin.time.Duration.Companion.minutes

/** Validate metadata emitted by the service, rather than just round-tripping its JSON. */
@SpringBootTest
@AutoConfigureMockMvc
class RegistrarDatasetMetadataTest {
    @Autowired lateinit var mockMvc: MockMvc
    private val validator = IssuerMetadataValidator(accessCertificates = TrustedCertificates { setOf(pki.signumChain.last()) })
    private val policy = ValidationPolicy(TrustPolicy.RequireAuthorizedSigner,
        StatusPolicy.ValidateIfPresent(TrustPolicy.RequireAuthorizedSigner), 5.minutes, true)

    @Test fun `emitted dataset with authorized WRPAC is accepted without an optional WRPRC`() = runBlocking<Unit> {
        val result = validator.validate(fetchSignedMetadata(), ISSUER, policy)
        result.report.decision shouldBe ValidationDecision.ACCEPTED
        result.issuer.shouldNotBeNull().registrarDataset.registryURI shouldBe "https://registrar.example/issuers/test"
        result.metadata.shouldNotBeNull().credentialIssuer shouldBe ISSUER
    }

    @Test fun `authentically signed missing malformed or duplicate datasets are rejected`() = runBlocking<Unit> {
        val metadata = JwsCompactTyped<IssuerMetadata>(fetchSignedMetadata()).payload
        val entry = metadata.issuerInfo!!.single()
        val dataset = entry.data.jsonObject
        val certificateOnly = listOf(IssuerInfo("registration_cert", JsonPrimitive(
            javaClass.getResource("/metadata-registration.jwt")!!.readText().trim()
        )))
        val invalid: List<List<IssuerInfo>?> = listOf(null, emptyList(), certificateOnly, listOf(entry, entry),
            listOf(entry.copy(data = JsonPrimitive("not an object"))),
            listOf(entry.copy(data = JsonObject(dataset - "registryURI"))),
            listOf(entry.copy(data = JsonObject(dataset + ("identifier" to JsonArray(emptyList()))))))
        for (info in invalid) {
            // Re-sign each malformed payload so the registration check, not a broken signature, must reject it.
            val signed = SignJwt<IssuerMetadata>(pki.keyMaterial, { header, _ ->
                header.copy(certificateChain = pki.signumChain.dropLast(1))
            })(OpenIdConstants.ISSUER_METADATA_JWT_TYPE, metadata.copy(issuerInfo = info), IssuerMetadata.serializer()).getOrThrow()
            val result = validator.validate(signed.toString(), ISSUER, policy)
            result.report.decision shouldBe ValidationDecision.REJECTED
            result.report.namedChecks.single { it.name == "signature" }.outcome shouldBe CheckOutcome.Passed
            result.report.namedChecks.single { it.name == "trust" }.outcome shouldBe CheckOutcome.Passed
            result.report.namedChecks.single { it.name == "registration" }.satisfied shouldBe false
            result.report.firstRejection().shouldNotBeNull()
        }
    }

    private fun fetchSignedMetadata(): String {
        val initial = mockMvc.get("/.well-known/openid-credential-issuer") {
            accept = MediaType.parseMediaType("application/jwt")
        }.andExpect { request { asyncStarted() } }.andReturn()
        return mockMvc.perform(asyncDispatch(initial)).andExpect(status().isOk).andReturn().response.contentAsString
    }

    companion object {
        private const val ISSUER = "https://issuer.example"
        private val pki = MetadataTestPki()
        private val directory = Files.createTempDirectory("dataset-metadata-test")
        @JvmStatic @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val store = KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry("signer", pki.leafKey.private, "test-only".toCharArray(), pki.chain.toTypedArray())
            }
            val path = directory.resolve("wrpac.p12")
            Files.newOutputStream(path).use { store.store(it, "test-only".toCharArray()) }
            mapOf(
                "backend.public-context" to ISSUER,
                "backend.metadata-key.type" to "KEYSTORE",
                "backend.metadata-key.keystore.path" to path.toUri().toString(),
                "backend.metadata-key.keystore.type" to "PKCS12",
                "backend.metadata-key.keystore.alias" to "signer",
                "backend.metadata-key.keystore.password" to "test-only",
                "backend.metadata-key.keystore.alias-password" to "test-only",
                "backend.metadata-registrar-dataset" to "classpath:metadata-registrar-dataset.json",
            ).forEach { (key, value) -> registry.add(key) { value } }
        }
    }
}
