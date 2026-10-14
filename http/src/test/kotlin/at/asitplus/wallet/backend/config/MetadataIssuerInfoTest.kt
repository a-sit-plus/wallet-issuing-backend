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

    @Test fun `malformed and incomplete configured datasets fail loading`() {
        val invalid = listOf("not-json", "[]", "{}") +
            listOf("identifier", "srvDescription", "registryURI", "providesAttestations").flatMap { field ->
                listOf(JsonObject(dataset - field).toString(), JsonObject(dataset + (field to
                    if (field == "registryURI") JsonPrimitive("") else JsonArray(emptyList()))).toString())
            }
        invalid.forEach { json ->
            val path = directory.resolve("dataset.json")
            Files.writeString(path, json)
            shouldThrowAny {
                loadMetadataIssuerInfo(BackendConfigurationProperties(metadataRegistrarDataset = path.toUri().toString()), resources)
            }
        }
    }
}
