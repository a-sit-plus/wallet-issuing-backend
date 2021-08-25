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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*


class IssuerCredentialRandomDataProvider(
    private val defaultLifetime: Duration,
    private val fallbackPhoto: String
) : IssuerCredentialDataProvider {
    private val desiredPictureSize = "256"
    private val client = OkHttpClient()

    inner class PupilAttributes {
        val randomGender = listOf("male", "female").random()
        val randomSchool = listOf(
            "Schiller", "Tesla", "Newton", "Einstein", "Marie Curie", "Rosalind Franklin",
            "Anne Frank", "Geschwister Scholl"
        ).random() + " " + listOf(
            "Realgymnasium", "Volksschule", "Gymnasium",
            "Mittelschule", "HTL", "HAK", "Hauptschule"
        ).random()
        val schoolCode = (1..6).map { "01".random() }.joinToString("") // e.g. 101010
        val pupilIdNumber = (1..2)
            .map { (1..8).map { "0123456789".random() }.joinToString("") }
            .joinToString("/") // e.g. 00200000/00000004
        val schoolClass = "12345".random().toString() + "ABCDEF".random()
        val birthDate = run {
            val maxAge = 18 * 12 * 31
            val minAge = 6 * 12 * 31
            val upperBound = maxAge - minAge + 1
            LocalDate.now().minusDays(minAge + Random().nextInt(upperBound).toLong())
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        }


        private val name = object {
            val nameArray =
                executeGet("https://www.behindthename.com/api/random.json?usage=ger&gender=${randomGender[0]}&key=lu244794741")
                    .takeIf { it.isSuccessful }
                    ?.body()?.string()?.let { jsonString -> Json.parseToJsonElement(jsonString).jsonObject }
                    ?.get("names")?.jsonArray
        }
        val firstName = name.nameArray?.get(1)?.jsonPrimitive?.content ?: "Susanne"
        val lastName = name.nameArray?.get(0)?.jsonPrimitive?.content ?: "Meier"

        private val address = object {
            val rawStr = client.newCall(buildRequest()).execute()
                .takeIf { it.isSuccessful }
                ?.body()?.string()?.let { Json.parseToJsonElement(it).jsonArray }
                ?.get(0)?.jsonPrimitive?.content

            fun buildRequest() = Request.Builder()
                .url("https://randommer.io/random-address")
                .post(FormBody.Builder().add("number", "1").add("culture", "de_AT").build())
                .build()
        }

        val school = address.rawStr?.split(",")?.toMutableList()?.also { it.removeAt(1) }
            ?.toList()?.joinToString(",") ?: "Breitenseer Straße 13, 1140, Wien, Austria"

        val county = address.rawStr?.split(",")?.get(3) ?: "Wien"

        val zip = address.rawStr?.split(",")?.get(2) ?: "1010"


        var encodedPhoto =
            executeGetWithApiKey("https://api.generated.photos/api/v1/faces?age=child&page=1&per_page=1&gender=$randomGender")
                .takeIf { it.isSuccessful }
                ?.let { jsonResp ->
                    val body = jsonResp.body()?.string()?.let { Json.parseToJsonElement(it).jsonObject }
                    val faces = body?.get("faces")?.jsonArray
                    val face = faces?.get(0)?.jsonObject
                    val urls = face?.get("urls")?.jsonArray
                    val picture = urls?.find { url -> url.jsonObject.containsKey(desiredPictureSize) }?.jsonObject
                    picture?.get(desiredPictureSize)?.jsonPrimitive?.content?.let { loadPicture(it) }
                } ?: fallbackPhoto
    }

    private fun loadPicture(url: String): String? {
        return executeGet(url).takeIf { it.isSuccessful }?.body()?.bytes()
            ?.let { Base64Utils.encode(it).decodeToString() }
    }

    private fun executeGet(url: String) = client.newCall(Request.Builder().url(url).build()).execute()

    private fun executeGetWithApiKey(url: String) =
        client.newCall(Request.Builder().url(url).addHeader("Authorization", "API-Key L6VjhxOfJUKWu3xrLnwVIg").build())
            .execute()

    override fun getClaim(subjectId: String, attribute: String) = run { PupilAttributes() }.let {
        when (attribute) {
            "photo" -> Claim(attribute, it.encodedPhoto, "image/jpeg", defaultLifetime)
            "schulname" -> Claim(attribute, it.randomSchool, "application/text", defaultLifetime)
            "schuladresse" -> Claim(attribute, it.school, "application/text", defaultLifetime)
            "schulkennzahl" -> Claim(attribute, it.schoolCode, "application/text", defaultLifetime)
            "schülerkennzahl" -> Claim(attribute, it.pupilIdNumber, "application/text", defaultLifetime)
            "vorname" -> Claim(attribute, it.firstName, "application/text", defaultLifetime)
            "nachname" -> Claim(attribute, it.lastName, "application/text", defaultLifetime)
            "titelvor" -> Claim(attribute, "", "application/text", defaultLifetime)
            "titelnach" -> Claim(attribute, "", "application/text", defaultLifetime)
            "geburtsdatum" -> Claim(attribute, it.birthDate, "application/text", defaultLifetime)
            "gültigBis" -> Claim(attribute, "2021-07-31", "application/text", defaultLifetime)
            "wohnort" -> Claim(attribute, it.county, "application/text", defaultLifetime)
            "wohnort-plz" -> Claim(attribute, it.zip, "application/text", defaultLifetime)
            "klasse" -> Claim(attribute, it.schoolClass, "application/text", defaultLifetime)
            else -> null
        }
    }
}