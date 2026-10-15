package at.asitplus.wallet.backend.config

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.DefaultResourceLoader
import java.nio.file.Files
import java.nio.file.Path

class MetadataIssuerInfoTest {
    @TempDir lateinit var directory: Path
    private val resources = DefaultResourceLoader()

    @Test fun `registration certificate is optional`() {
        loadMetadataIssuerInfo(BackendConfigurationProperties(), resources).shouldBeNull()
        val registration = resources.getResource("classpath:metadata-registration.jwt").inputStream.bufferedReader().use { it.readText() }.trim()
        val info = loadMetadataIssuerInfo(BackendConfigurationProperties(metadataRegistrationCertificate = "classpath:metadata-registration.jwt"), resources)!!
        info.single().format shouldBe "registration_cert"
        info.single().data shouldBe JsonPrimitive(registration)
    }

    @Test fun `empty registration certificate fails loading`() {
        val path = directory.resolve("registration.jwt")
        Files.writeString(path, " \n")
        shouldThrowAny {
            loadMetadataIssuerInfo(BackendConfigurationProperties(metadataRegistrationCertificate = path.toUri().toString()), resources)
        }
    }
}
