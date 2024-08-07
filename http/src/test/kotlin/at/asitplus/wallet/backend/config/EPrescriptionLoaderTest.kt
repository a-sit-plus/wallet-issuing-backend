package at.asitplus.wallet.backend.config

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.converter.json.KotlinSerializationJsonHttpMessageConverter

class EPrescriptionLoaderTest {

    @Disabled("Need to enter correct URL and api-key")
    @Test
    fun test() {
        val builder = RestTemplateBuilder()
            .messageConverters(KotlinSerializationJsonHttpMessageConverter(Json {
                ignoreUnknownKeys = true
            })) // see BackendConfiguration.kt
        val service = EPrescriptionLoader(builder, "https://example.com/", "TODO")

        val result = service.load("thisIsA/bPK/GH/012345==", "Wolfgang Peter", "Huber", "2000-03-04")

        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        println(data)
    }
}