package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.config.MetadataTestPki
import at.asitplus.wallet.lib.jws.VerifyJwsSignature
import at.asitplus.signum.indispensable.josef.JwsCompact
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

@SpringBootTest
@AutoConfigureMockMvc
class ConfiguredIssuerMetadataTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `configured endpoint includes WRPAC chain and WRPRC for the complete catalog`() = runBlocking {
        val initial = mockMvc.get("/.well-known/openid-credential-issuer") {
            accept = MediaType.parseMediaType("application/jwt")
        }.andExpect { request { asyncStarted() } }.andReturn()
        val response = mockMvc.perform(asyncDispatch(initial)).andExpect(status().isOk).andReturn().response
        response.contentType shouldBe "application/jwt"
        response.getHeader("Vary") shouldBe "Accept"
        val signed = JwsCompact(response.contentAsString)
        VerifyJwsSignature()(signed, pki.signumChain.first().decodedPublicKey.getOrThrow()).getOrThrow()
        signed.jwsHeader.certificateChain!!.map { it.encodeToDer().toList() } shouldBe
            listOf(pki.leaf, pki.intermediate).map { it.encoded.toList() }
        val payload = Json.parseToJsonElement(signed.plainPayload.decodeToString()).jsonObject
        payload["credential_issuer"]!!.jsonPrimitive.content shouldBe "https://issuer.example"
        payload["sub"] shouldBe payload["credential_issuer"]
        val entries = payload["issuer_info"]!!.jsonArray.associate { it.jsonObject["format"]!!.jsonPrimitive.content to it.jsonObject["data"]!! }
        entries["registration_cert"]!!.jsonPrimitive.content shouldBe registration
        entries.keys shouldBe setOf("registration_cert")
        signed.jwsHeader.type shouldBe "openidvci-issuer-metadata+jwt"
        check(payload["iat"]!!.jsonPrimitive.long > 0)
        check(payload["credential_configurations_supported"]!!.jsonObject.size >= 9)
    }

    companion object {
        private val pki = MetadataTestPki()
        private val registration = ConfiguredIssuerMetadataTest::class.java.getResource("/metadata-registration.jwt")!!.readText().trim()
        private val directory = Files.createTempDirectory("issuer-metadata-test")
        private val password = "test-only"

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val store = KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry("signer", pki.leafKey.private, password.toCharArray(), pki.chain.toTypedArray())
            }
            val keyPath = directory.resolve("wrpac.p12")
            Files.newOutputStream(keyPath).use { store.store(it, password.toCharArray()) }
            val registrationPath = directory.resolve("wrprc.jwt")
            Files.writeString(registrationPath, registration)
            val properties = mapOf(
                "backend.public-context" to "https://issuer.example",
                "backend.metadata-key.type" to "KEYSTORE",
                "backend.metadata-key.keystore.path" to keyPath.toUri().toString(),
                "backend.metadata-key.keystore.type" to "PKCS12",
                "backend.metadata-key.keystore.alias" to "signer",
                "backend.metadata-key.keystore.password" to password,
                "backend.metadata-key.keystore.alias-password" to password,
                "backend.metadata-registration-certificate" to registrationPath.toUri().toString(),
            )
            properties.forEach { (key, value) -> registry.add(key) { value } }
        }
    }
}
