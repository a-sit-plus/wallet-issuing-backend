package at.asitplus.wallet.backend.config

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.DefaultResourceLoader
import java.nio.file.Path
import java.nio.file.Files

class MetadataIssuerInfoTest {
    @TempDir lateinit var directory: Path
    private val resources = DefaultResourceLoader()
    private val dataset get() = resources.getResource("classpath:metadata-registrar-dataset.json")
        .inputStream.bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }

    @Test fun `dataset and certificate are independently optional`() {
        loadMetadataIssuerInfo(BackendConfigurationProperties(), resources).shouldBeNull()
        val config = BackendConfigurationProperties(metadataRegistrarDataset = "classpath:metadata-registrar-dataset.json")
        val info = loadMetadataIssuerInfo(config, resources)!!
        info.single().format shouldBe "registrar_dataset"
        info.single().data shouldBe dataset
        val both = loadMetadataIssuerInfo(config.copy(metadataRegistrationCertificate = "classpath:metadata-registration.jwt"), resources)!!
        both.map { it.format } shouldBe listOf("registrar_dataset", "registration_cert")
    }

    @Test fun `issuer_info exports and datasets not matching VC-K are accepted`() {
        val registrar = """{"identifier":[{"type":"http://data.europa.eu/eudi/id/VATIN","identifier":"U60778947"}],""" +
            """"srvDescription":[{"lang":"en","value":"Testing"}],"registryURI":"https://registrar.example/wrp/1",""" +
            """"credential":[{"format":"dc+sd-jwt","meta":{"vct_values":["urn:eudi:pid:1"]}}]}"""
        listOf(
            registrar to registrar,
            """[{"data":$registrar,"format":"registrar_dataset"}]""" to registrar,
            """{"format":"registrar_dataset","data":$registrar}""" to registrar,
            """[{"format":"registration_cert","data":"a.b.c"},{"format":"registrar_dataset","data":$dataset}]""" to dataset.toString(),
            JsonObject(dataset - "providesAttestations").toString() to JsonObject(dataset - "providesAttestations").toString(),
        ).forEach { (json, expected) ->
            loadMetadataIssuerInfo(configWith(json), resources)!!.single().data shouldBe Json.parseToJsonElement(expected)
        }
    }

    @Test fun `malformed configured datasets fail loading`() {
        listOf(
            "not-json", "[]", "{}", "\"text\"", "[1]", "[{}]",
            """[{"format":"registration_cert","data":"a.b.c"}]""",
            """[{"format":"registrar_dataset","data":{}}]""",
            """[{"format":"registrar_dataset","data":"text"}]""",
            """[{"format":"registrar_dataset","data":$dataset},{"format":"registrar_dataset","data":$dataset}]""",
        ).forEach { json ->
            shouldThrowAny { loadMetadataIssuerInfo(configWith(json), resources) }
        }
    }

    private fun configWith(json: String): BackendConfigurationProperties {
        val path = directory.resolve("dataset.json")
        Files.writeString(path, json)
        return BackendConfigurationProperties(metadataRegistrarDataset = path.toUri().toString())
    }
}
