package at.asitplus.wallet.backend

import at.asitplus.openid.IssuerMetadata
import at.asitplus.openid.OpenIdConstants
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.lib.jws.VerifyJwsObject
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header

@SpringBootTest(properties = ["backend.metadata-registration-certificate=classpath:metadata-registration.jwt"])
@AutoConfigureMockMvc
class IssuerMetadataContentNegotiationTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var configuration: BackendConfigurationProperties

    @Test
    fun `signed metadata includes the registrar token unchanged under its signature`() = runBlocking {
        val signed = JwsCompactTyped<IssuerMetadata>(fetchMetadata(MediaType.parseMediaType("application/jwt")))
        VerifyJwsObject()(signed.jws).getOrThrow()
        signed.payload.issuerInfo!!.single().format shouldBe "registration_cert"
        val wirePayload = kotlinx.serialization.json.Json.parseToJsonElement(
            String(Base64.getUrlDecoder().decode(signed.jws.toString().split('.')[1]))
        ).jsonObject
        val entry = wirePayload["issuer_info"]!!.jsonArray.single().jsonObject
        entry["format"]!!.jsonPrimitive.content shouldBe "registration_cert"
        entry["data"]!!.jsonPrimitive.content shouldBe
            javaClass.getResource("/metadata-registration.jwt")!!.readText().trim()
        Unit
    }

    @Test
    fun `issuer metadata is available as a signed JWT for wallet trust validation`() = runBlocking {
        val signed = JwsCompactTyped<IssuerMetadata>(fetchMetadata(MediaType.parseMediaType("application/jwt")))

        signed.jws.jwsHeader.type shouldBe OpenIdConstants.ISSUER_METADATA_JWT_TYPE
        VerifyJwsObject()(signed.jws).getOrThrow()
        signed.payload.subject shouldBe signed.payload.credentialIssuer
        signed.payload.issuedAt.shouldNotBeNull()
        signed.payload.displayProperties.shouldNotBeNull().map { it.name }
            .shouldContainExactly(configuration.metadata.name)
        Unit
    }

    @Test
    fun `unsigned issuer metadata carries display properties but no JWT claims`() {
        val metadata = joseCompliantSerializer.decodeFromString<IssuerMetadata>(
            fetchMetadata(MediaType.APPLICATION_JSON)
        )

        metadata.displayProperties.shouldNotBeNull().map { it.name }.shouldContainExactly(configuration.metadata.name)
        metadata.subject.shouldBeNull()
        metadata.issuedAt.shouldBeNull()
        metadata.issuerInfo.shouldBeNull()
    }

    @Test
    fun `metadata prefers JSON when JWT has lower quality or is excluded`() {
        listOf("application/jwt;q=0.1, application/json;q=1", "application/jwt;q=0, application/json").forEach { accept ->
            val initial = mockMvc.get("/.well-known/openid-credential-issuer") {
                header(HttpHeaders.ACCEPT, accept)
            }.andExpect { request { asyncStarted() } }.andReturn()
            mockMvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.VARY, "Accept"))
        }
    }

    private fun fetchMetadata(accept: MediaType): String {
        val initial = mockMvc.get("/.well-known/openid-credential-issuer") {
            this.accept = accept
        }.andExpect { request { asyncStarted() } }.andReturn()
        return mockMvc.perform(asyncDispatch(initial))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.VARY, "Accept"))
            .andExpect(content().contentTypeCompatibleWith(accept))
            .andReturn().response.contentAsString
    }
}
