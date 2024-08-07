package at.asitplus.wallet.backend.config

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

class EPrescriptionLoader(
    restTemplateBuilder: RestTemplateBuilder,
    val url: String,
    apiKey: String,
) {
    private val restTemplate = restTemplateBuilder
        .defaultHeader("X-ApiToken", apiKey)
        .build()

    fun load(bpk: String, givenName: String, familyName: String, birthDate: String): Result<OttResponse?> =
        kotlin.runCatching {
            restTemplate.postForObject(
                url,
                HttpEntity<OttRequest>(OttRequest(
                    identifier = OttRequestBpkHolder(system = "urn:oid:1.2.3.4", value = bpk),
                    familyName = familyName,
                    givenName = givenName,
                    birthDate = LocalDate.parse(birthDate)
                ), HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
                OttResponse::class.java
            )
        }
}

@Serializable
data class OttRequest(
    @SerialName("identifier")
    val identifier: OttRequestBpkHolder,

    @SerialName("familyName")
    val familyName: String,

    @SerialName("givenName")
    val givenName: String,

    @SerialName("birthDate")
    val birthDate: LocalDate
)

@Serializable
data class OttRequestBpkHolder(
    @SerialName("system")
    val system: String,

    @SerialName("value")
    val value: String,
)

@Serializable
data class OttResponse(
    @SerialName("status")
    val status: String? = null,

    @SerialName("message")
    val message: String? = null,

    @SerialName("info")
    val info: String? = null,

    @SerialName("data")
    val data: OttData
)

@Serializable
data class OttData(
    @SerialName("oneTimeToken")
    val oneTimeToken: String,

    @SerialName("countryCode")
    val countryCode: String,

    @SerialName("ottValidUntilISO")
    val ottValidUntil: Instant,

    @SerialName("myHealthEU_id")
    val euId: String? = null,
)