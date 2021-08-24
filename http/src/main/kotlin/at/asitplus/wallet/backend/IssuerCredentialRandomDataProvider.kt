package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.Claim
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.util.Base64Utils
import java.time.Duration

class IssuerCredentialRandomDataProvider(
    private val defaultLifetime: Duration,
    private val fallbackPhoto: String
) : IssuerCredentialDataProvider {

    private val desiredPictureSize = "256"
    private val client = OkHttpClient()
    private val randomGender = listOf("male", "female").random()

    private val name = object {
        private val nameArray =
            executeGet("https://www.behindthename.com/api/random.json?usage=ger&gender=${randomGender[0]}&key=lu244794741")
                .takeIf { it.isSuccessful }
                ?.let {
                    it.body()?.string()?.let { jsonString -> Json.parseToJsonElement(jsonString).jsonObject }
                        ?.get("names")?.jsonArray
                }

        val firstName = nameArray?.get(1)?.jsonPrimitive?.content ?: "Susanne"
        val lastName = nameArray?.get(0)?.jsonPrimitive?.content ?: "Meier"
    }

    private var address = object {

        private val rawAddressStr = client.newCall(buildRequest()).execute()
            .takeIf { it.isSuccessful }
            ?.body()?.string()?.let { Json.parseToJsonElement(it).jsonArray }
            ?.get(0)?.jsonPrimitive?.content

        private fun buildRequest() = Request.Builder()
            .url("https://randommer.io/random-address")
            .post(FormBody.Builder().add("number", "1").add("culture", "de_AT").build())
            .build()

        val school = rawAddressStr?.split(",")?.toMutableList()?.also { it.removeAt(1) }
            ?.toList()?.joinToString(",") ?: "Breitenseer Straße 13, 1140, Wien, Austria"

        val county = rawAddressStr?.split(",")?.get(3) ?: "Wien"

        val zip = rawAddressStr?.split(",")?.get(2) ?: "1010"
    }


    private var encodedPhoto =
        executeGetWithApiKey("https://api.generated.photos/api/v1/faces?age=child&page=1&per_page=1&gender=$randomGender")
            .takeIf { it.isSuccessful }
            ?.let { jsonResp ->
                val body = jsonResp.body()?.string()?.let { Json.parseToJsonElement(it).jsonObject }
                val faces = body?.get("faces")?.jsonArray
                val face = faces?.get(0)?.jsonObject
                val urls = face?.get("urls")?.jsonArray
                val picture = urls?.find { url -> url.jsonObject.containsKey(desiredPictureSize) }?.jsonObject
                picture?.get(desiredPictureSize)?.jsonPrimitive?.content?.let(this::loadPicture)
            } ?: fallbackPhoto

    private fun loadPicture(url: String): String? {
        return executeGet(url).takeIf { it.isSuccessful }?.body()?.bytes()
            ?.let { Base64Utils.encode(it).decodeToString() }
    }

    private fun executeGet(url: String) = client.newCall(Request.Builder().url(url).build()).execute()

    private fun executeGetWithApiKey(url: String) =
        client.newCall(Request.Builder().url(url).addHeader("Authorization", "API-Key L6VjhxOfJUKWu3xrLnwVIg").build())
            .execute()

    override fun getClaim(subjectId: String, attribute: String) = when (attribute) {
        "photo" -> Claim(attribute, encodedPhoto, "image/jpeg", defaultLifetime)
        "schulname" -> Claim(attribute, "Quarto Testschule", "application/text", defaultLifetime)
        "schuladresse" -> Claim(attribute, address.school, "application/text", defaultLifetime)
        "schulkennzahl" -> Claim(attribute, "101010", "application/text", defaultLifetime)
        "schülerkennzahl" -> Claim(attribute, "00200000/00000004", "application/text", defaultLifetime)
        "vorname" -> Claim(attribute, name.firstName, "application/text", defaultLifetime)
        "nachname" -> Claim(attribute, name.lastName, "application/text", defaultLifetime)
        "titelvor" -> Claim(attribute, "", "application/text", defaultLifetime)
        "titelnach" -> Claim(attribute, "", "application/text", defaultLifetime)
        "geburtsdatum" -> Claim(attribute, "1997-12-26", "application/text", defaultLifetime)
        "gültigBis" -> Claim(attribute, "2021-07-31", "application/text", defaultLifetime)
        "wohnort" -> Claim(attribute, address.county, "application/text", defaultLifetime)
        "wohnort-plz" -> Claim(attribute, address.zip, "application/text", defaultLifetime)
        "klasse" -> Claim(attribute, "3B", "application/text", defaultLifetime)
        else -> null
    }
}