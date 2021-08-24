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

    fun getResponse(url: String) = client.newCall(Request.Builder().url(url).build()).execute()

    // random Parameters
    private val gender = listOf("male", "female").random()
    private val gen_ = when (gender) {
        "male" -> "m"
        else -> "f"
    }
    private val name = object {
        private val nameArray =
            getResponse("https://www.behindthename.com/api/random.json?usage=ger&gender=${gen_}&key=lu244794741")
                .takeIf { it.isSuccessful }
                ?.let {
                    it.body()?.string()?.let { jsonString -> Json.parseToJsonElement(jsonString).jsonObject }
                        ?.get("names")?.jsonArray
                }

        val firstName = nameArray?.get(1)?.jsonPrimitive?.content ?: "Susanne"
        val lastName = nameArray?.get(0)?.jsonPrimitive?.content ?: "Meier"
    }

    private var address = object {

        private val rawAddressStr = client.newCall(
            Request.Builder()
                .url("https://randommer.io/random-address")
                .post(
                    FormBody.Builder()
                        .add("number", "1")
                        .add("culture", "de_AT")
                        .build()
                )
                .build()
        ).execute()
            .takeIf { it.isSuccessful }
            ?.let { response ->
                response.body()?.string()?.let { Json.parseToJsonElement(it).jsonArray }
                    ?.get(0)?.jsonPrimitive?.content
            }

        val school = rawAddressStr?.split(",")?.toMutableList()?.also { it.removeAt(1) }
            ?.toList()?.joinToString(",") ?: "Breitenseer Straße 13, 1140, Wien, Austria"

        val county = rawAddressStr?.split(",")?.get(3) ?: "Wien"

        val zip = rawAddressStr?.split(",")?.get(2) ?: "1010"
    }


    private var encodedPhoto = Request.Builder()
        .url("https://api.generated.photos/api/v1/faces?age=child&page=1&per_page=1&gender=${gender}")
        .addHeader("Authorization", "API-Key L6VjhxOfJUKWu3xrLnwVIg")
        .build().let { request1 ->
            client.newCall(request1).execute()
                .takeIf { it.isSuccessful }
                ?.let { jsonResp ->
                    val body = jsonResp.body()?.string()?.let { Json.parseToJsonElement(it).jsonObject }
                    val faces = body?.get("faces")?.jsonArray
                    val face = faces?.get(0)?.jsonObject
                    val urls = face?.get("urls")?.jsonArray
                    val picture = urls?.find { url -> url.jsonObject.containsKey(desiredPictureSize) }?.jsonObject
                    val picUrl = picture?.get(desiredPictureSize)?.jsonPrimitive?.content

                    picUrl?.let {
                        url -> getResponse(url)
                            .takeIf { it.isSuccessful }
                            ?.let { picResp ->
                                picResp.body()?.bytes()?.let {
                                    Base64Utils.encode(it).decodeToString()
                                }
                            }
                    }
                }
        } ?: fallbackPhoto

    override fun getClaim(subjectId: String, attribute: String) = when (attribute) {
        "photo" -> Claim(attribute, encodedPhoto, "image/jpeg", defaultLifetime)
        "schulname" -> Claim(attribute, "Quarto Testschule", "application/text", defaultLifetime)
        "schuladresse" -> Claim(
            attribute,
            address.school,
            "application/text",
            defaultLifetime
        )
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