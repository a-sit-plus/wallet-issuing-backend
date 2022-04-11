package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.AtomicAttributeCredential
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.CredentialSubject
import at.asitplus.wallet.lib.data.PupilIdCredential
import at.asitplus.wallet.lib.data.SchemaIndex
import at.asitplus.wallet.lib.decodeBase64ToArray
import at.asitplus.wallet.lib.encodeBase64
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Random
import kotlin.time.Duration


/**
 * Provides random credential data for the currently logged-in user
 */
class RandomCredentialDataProvider constructor(
    private val lifetime: Duration,
    private val listOfPhotos: Map<String, ByteArray>,
    private val deviceBindingStorageService: DeviceBindingStorageService,
) : IssuerCredentialDataProvider {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val randomAttributeCache: MutableMap<String, RandomAttributeSet> = mutableMapOf()

    inner class RandomAttributeSet {
        val randomGender = listOf("male", "female").random()
        private val randomSchoolPrefix = listOf(
            "Schiller", "Tesla", "Newton", "Einstein", "Marie Curie", "Rosalind Franklin",
            "Anne Frank", "Geschwister Scholl"
        ).random()
        private val randomSchoolSuffix = listOf(
            "Realgymnasium", "Volksschule", "Gymnasium",
            "Mittelschule", "HTL", "HAK", "Hauptschule"
        ).random()
        val schoolName = "$randomSchoolPrefix $randomSchoolSuffix"
        val schoolNumber = (1..6).map { "01".random() }.joinToString("") // e.g. 101010
        val pupilNumber =  // e.g. 00200000/00000004
            (1..2).joinToString("/") { (1..8).map { "0123456789".random() }.joinToString("") }
        val dateOfBirth: String = run {
            val maxAge = 18 * 12 * 31
            val minAge = 6 * 12 * 31
            val upperBound = maxAge - minAge + 1
            LocalDate.now().minusDays(minAge + Random().nextInt(upperBound).toLong())
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        }
        val firstName = if (randomGender == "male") {
            listOf("Lukas", "Tobias", "Maximilian", "Luca", "David").random()
        } else {
            listOf("Anna", "Hannah", "Lena", "Sarah", "Sophie").random()
        }
        val lastName = listOf(
            "Gruber", "Huber", "Wagner", "Müller",
            "Pichler", "Moser", "Steiner", "Maier"
        ).random()

        val schoolAddress = "Musterstraße 10, 1010 Wien"
        val city = listOf("Wien", "Mödling", "Linz", "Salzburg", "Innsbruck", "Klagenfurt", "Graz").random()
        val zip = listOf("1010", "2050", "4050", "5060", "6070", "7080", "8090").random()
        var encodedPhoto: ByteArray = listOfPhotos
            .filter { it.key[0] == randomGender[0] }
            .values
            .ifEmpty { listOf(fallbackPhoto.decodeBase64ToArray()!!) }.random()
    }

    override fun getClaim(subjectId: String, attributeName: String): CredentialSubject? {
        val deviceBinding = deviceBindingStorageService.getDeviceBindingForCurrentUser()
            ?: return null.also {
                log.error("Got no authenticated user when trying to issue credentials")
            }
        if (deviceBinding.keyId != subjectId)
            return null.also {
                log.error(
                    "Got invalid keyId ('{}') from authenticated user when trying to issue credentials for ('{}')",
                    deviceBinding.keyId, subjectId
                )
            }

        val it = randomAttributeCache[subjectId]
            ?: RandomAttributeSet().also { randomAttributeCache[subjectId] = it }
        return when {
            attributeName.startsWith(SchemaIndex.ATTR_GENERIC_PREFIX) -> {
                when (attributeName.removePrefix(SchemaIndex.ATTR_GENERIC_PREFIX + "/")) {
                    "vorname" -> AtomicAttributeCredential(subjectId, attributeName, it.firstName)
                    "nachname" -> AtomicAttributeCredential(subjectId, attributeName, it.lastName)
                    else -> null
                }
            }
            attributeName.startsWith(SchemaIndex.ATTR_GREEN_PASS_PREFIX) -> {
                when (attributeName.removePrefix(SchemaIndex.ATTR_GREEN_PASS_PREFIX + "/")) {
                    "name" -> AtomicAttributeCredential(subjectId, attributeName, "${it.firstName} ${it.lastName}")
                    "date-of-birth" -> AtomicAttributeCredential(subjectId, attributeName, it.dateOfBirth)
                    "photo" -> AtomicAttributeCredential(
                        subjectId,
                        attributeName,
                        it.encodedPhoto.encodeBase64(),
                        "image/jpeg"
                    )
                    "vaccination" -> AtomicAttributeCredential(
                        subjectId,
                        attributeName,
                        "Moderna 2/2, on 2021-08-10, Vienna"
                    )
                    "test" -> AtomicAttributeCredential(subjectId, attributeName, "Negative RAT, on 2021-09-10, Graz")
                    "recovery" -> AtomicAttributeCredential(subjectId, attributeName, "Recovered, on 2021-02-10")
                    else -> null
                }
            }
            else -> null
        }
    }

    override fun getCredential(subjectId: String, attributeType: String): CredentialSubject? {
        val deviceBinding = deviceBindingStorageService.getDeviceBindingForCurrentUser()
            ?: return null.also {
                log.error("Got no authenticated user when trying to issue credentials")
            }

        if (deviceBinding.keyId != subjectId)
            return null.also {
                log.error(
                    "Got invalid keyId ('{}') from authenticated user when trying to issue credentials for ('{}')",
                    deviceBinding.keyId, subjectId
                )
            }

        val it = randomAttributeCache[subjectId]
            ?: RandomAttributeSet().also { randomAttributeCache[subjectId] = it }

        return when (attributeType) {
            ConstantIndex.PupilId.vcType -> {
                PupilIdCredential(
                    id = subjectId,
                    schoolName = it.schoolName,
                    schoolAddress = it.schoolAddress,
                    schoolNumber = it.schoolNumber,
                    pupilNumber = it.pupilNumber,
                    firstName = it.firstName,
                    lastName = it.lastName,
                    dateOfBirth = it.dateOfBirth,
                    validUntil = "2023-09-01",
                    postCity = it.city,
                    postCode = it.zip,
                    picture = it.encodedPhoto
                )
            }
            else -> {
                null
            }
        }
    }

    override fun getLifetime(): Duration {
        return lifetime
    }

    private val fallbackPhoto = "/9j/4AAQSkZJRgABAQEBLAEsAAD//gATQ3JlYXRlZCB3aXRoIEdJTVD/4gIwSUNDX1BST0ZJTEUA\n" +
            "AQEAAAIgbGNtcwQwAABtbnRyR1JBWVhZWiAH5QAIAAUACwAdABxhY3NwQVBQTAAAAAAAAAAAAAAA\n" +
            "AAAAAAAAAAAAAAAAAAAA9tYAAQAAAADTLWxjbXMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\n" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAZkZXNjAAAAzAAAAG5jcHJ0AAABPAAAADZ3dHB0AAABdAAAABRr\n" +
            "VFJDAAABiAAAACBkbW5kAAABqAAAACRkbWRkAAABzAAAAFJtbHVjAAAAAAAAAAEAAAAMZW5VUwAA\n" +
            "AFIAAAAcAEcASQBNAFAAIABiAHUAaQBsAHQALQBpAG4AIABEADYANQAgAEcAcgBhAHkAcwBjAGEA\n" +
            "bABlACAAdwBpAHQAaAAgAHMAUgBHAEIAIABUAFIAQwAAbWx1YwAAAAAAAAABAAAADGVuVVMAAAAa\n" +
            "AAAAHABQAHUAYgBsAGkAYwAgAEQAbwBtAGEAaQBuAABYWVogAAAAAAAA81EAAQAAAAEWzHBhcmEA\n" +
            "AAAAAAMAAAACZmYAAPKnAAANWQAAE9AAAApbbWx1YwAAAAAAAAABAAAADGVuVVMAAAAIAAAAHABH\n" +
            "AEkATQBQbWx1YwAAAAAAAAABAAAADGVuVVMAAAA2AAAAHABEADYANQAgAEcAcgBhAHkAcwBjAGEA\n" +
            "bABlACAAdwBpAHQAaAAgAHMAUgBHAEIAIABUAFIAQwAA/9sAQwADAgIDAgIDAwMDBAMDBAUIBQUE\n" +
            "BAUKBwcGCAwKDAwLCgsLDQ4SEA0OEQ4LCxAWEBETFBUVFQwPFxgWFBgSFBUU/8IACwgAXgBGAQER\n" +
            "AP/EAB0AAAEFAAMBAAAAAAAAAAAAAAYDBAUHCAACCQH/2gAIAQEAAAAB0/ABA1OTwneKofSKYy9n\n" +
            "bSsz7mOAbwkoYzN9qYNUkLBMSBgXFHnWf6JZsR4rI3GCLY0lW/QfObIdedxVpOqLvlx846efb/WB\n" +
            "pCjCFgd8HpHGlYlmpZ6eF4pK6bFKWByvjmImDAoYHxo7BYQeb8a2OZwn/8QAJRAAAQQBBAEEAwAA\n" +
            "AAAAAAAAAwECBAUABhESExQQISIjJDFB/9oACAEBAAEFAjM7BukhrkmakI47NRn7o2opPaZgrJsa\n" +
            "OXzmN4Ny8tmwhTrjk6cXthP5ILy/sjWDtqmxDIlelq45jPrGsDMsGFahnqsOOUuBpysZUu8Gya9H\n" +
            "ek6UkiS07GAr6s0h0eh4OCFg8CuWMZo8prEMyD2NzsTNPRxSDBijCIo137UYs7USNJU2cSZlcIVd\n" +
            "jAkfnZmiDMWS+QGLHn6sA51YrrRbWieY1LGWM0Y0c5JiAb/NNzfFtbvynRU03K8jTlakIUmPs4R9\n" +
            "iRCfQ9rEz9NhqnPSd6yxiHRiYCa5Jcs09DHmC4VchSR3PXNt281a+s1U6vyJassocmYSGe3GSS2i\n" +
            "ag8hjREMNEfLY1kh2b5o+UQcTu5mYgSNZK7ZYy8WqRMuYSOO6glNKOmGFKSMoceuPfxTT2/Ug+Yx\n" +
            "j7sDHqpcixpfClGio1RfBxCe/P3qF/Fju+Esbyl//8QALhAAAgEDAgQEBgIDAAAAAAAAAQIAAxES\n" +
            "ITEQE0FRBCIjMhRCYWJxgSQzkaHh/9oACAEBAAY/ArRyBlUPQTDLFugU6TR8PvlqtVtDrbtLM68w\n" +
            "e1xsZybY1MsYFHTTgUU+of8AUFvFFmG+BtKFdR69TTLvGex10M03tDobXynhmZLuRbmdb8fd+SzW\n" +
            "vMnqI7W3Q6Tlp5Ftkv0aYtqCCIPKbzK2vaIXF0LAzfhlWqN+uktQeorfXrL4m0Ges2gieItop1lN\n" +
            "lGOHp6zea7RNNIAlMCXms5VFlUD31m2X/s5OVRi6X9S/mHeV6I1v58ZvbgFPunMrOEExoKz/AHEW\n" +
            "EY9t4F2pjsImRLFVCAt27QVfnwKfqBAt4JQJ0Utafw6edZ9L9pU+IYvl1O8tbUiFesxOjR5vcy8J\n" +
            "vZl1EVW/uUWYTK0svaE+KRSG09M6WgNE8+tcDBNTHyFmwOh4b7TIGxinlXqKdwdJTrUzdWEU0aXO\n" +
            "qH5b2gbxTs3ajS0UfvrDamF/UueukNo4RcFvYDiUvsdopY2gLm8FPw6XH0iy8YeGBNEvyqZf3M3U\n" +
            "/wCTCjqAB8w1E0Us3do9+vDykiO0Uo9m6g7GWUgON1MRPgAj2srjUiYZ5qwyXgeP54Z06rUXGmS9\n" +
            "p//EACYQAQACAgIBBAEFAQAAAAAAAAEAESExQVFhcYGRoRCxwdHh8PH/2gAIAQEAAT8hNFgsfhjn\n" +
            "s12QiU1Wp9OYxW2yJfuId5ca5tsPvzK9HgR/uIbI+Obh64Afg2aXdA7WKG5LUlZU8I/CLPj7iyMy\n" +
            "V09zWpRsemYiq3TXfP8AvEDmIbYUqnv+4fi0JRWjL8rx4l8BTar7hCPE+7MFg9NTJr1cH+5yolOO\n" +
            "9/rDJTvC3VTxk58b+oEIGyz8Wn7W99uJk0lO4OoaFp0y7YDuG0GDQoqLlbBHccZsHCtfUE0X3nVv\n" +
            "1S46ty1axkB2EAkVL9ep/C8cDuBK0IPa1/tOEcYuFS/2jiHfLKPBPRKLMUL5j4V/gM2oO7UHl2qP\n" +
            "JuWrENkOh4lfuU9Q/vL8E8xoTfcBvRKHgsPTLhoQaD0OIewUkg4B4kCgqapjp3VFzJZEvEwsGyBr\n" +
            "+Akc+ZppZUIct98S9qrtheQIM56uj3l7+Y9CaF5gUugJSUjTOc2FVHNkzUePHiMIwTY94sUD9f8A\n" +
            "ZecTwLCsD+Y0xS3klCjS29FRzbVvUdipYNRGlgYyRqBHjEIlsVpcPjPEHSy+/wDhHqJV1ndT7FD/\n" +
            "AKgJwsJKinPrH9ghjHRKuYRjTsP1Hzn0gWzQ3z3NnxGG3bPYrqDzeiUh0y+YlAEVg6blQTE5kzIB\n" +
            "cz2Po+J//9oACAEBAAAAEKcehdnjOix7J/vFIXfv/8QAJhABAQADAAICAgEEAwAAAAAAAREAITFB\n" +
            "UWGBcaGRELHB8NHh8f/aAAgBAQABPxBpwKX4HAgoCiowr9/7vBS10QLKt9e9w84LK3S9AoN+z359\n" +
            "ZFvWgKB4mDpwCUd4fQ1/ngS6EvRdc24I2sXZAieJu+spQvD1Al/WAvxjhLJoF1HJOG++nHgVUeMR\n" +
            "utHbp7iIhLTWJ4K0mtc+MT6GNSyw/K6X9bwJ7rGUND+Gv1kZ55pKAXxRdYMp4mLkPKvejfDB/Qjr\n" +
            "s8xlxxwb/eNpjPtplHC70Ic8nnxNiEUkgk34+bl2Nx9BT7B+8oW/YfA191rATNpEh/mYCsNl2pps\n" +
            "Qhfp6cFkFs6PMrj8ait0tCwCh9OGleBGnukh8TeeMigkLdazUbeAbOecPn2aCZYC9ZjLB0WhMF+3\n" +
            "Lq5GNzNvTCYtPkAYKDeYht+TOcACNbl9cwLfC7GL8DyuRBAWuBIGNQPP20D/AM0uAiU5LEfDpgxo\n" +
            "82snV34R4mLhFQCqF5+825AAf1/xgkgBfIHIBmq6obYGUmyFEXo9f95MaCgDHxhQq1mlDtZUXcy8\n" +
            "bwEX8AV/lzdJQmgJfzVhc9hSf8ZDNtx83FUoJ8Vl/ZitQz9SO0HQHdYKTgvC1fIuqa7jiFzG08X5\n" +
            "wJJxWZ9OPai50TThNCBPoscmJK2jwydIiHuUuLSEQ1XW/rEZSFuNBvR/TcQWKaZzHlxEUCNtfOLI\n" +
            "JU9u1WJrvMF3wDpC8AN1bj5w3hg0RqfvKCUnF4YTcw1VfX7P4y3yxFqITNmCNhjSC7KS+bqZOORU\n" +
            "q8r5Gj+MeCIMkL1ejDy5UDntX8YcaxB0YERkd9T5cMAJ298xksWacC/QABY2tVrb24HI6YPJzE6l\n" +
            "HB/u8EFOp1Ep/dyf71RMFZti0xIW6NAHVeBM1WpVOVdz45mvJOmmLYSBJEEBYprY0A4xrTtRrwrE\n" +
            "QUBlFEwUeSSfwDmAUPzB3BiQfTKu71If3xeNFp5sMXwZEqLpPhcT3xsXaelUoDZJZxsOZXJ25NLR\n" +
            "WrVKrtuFUBA6rIBKPk76OZr6Dx9XIJl7MYDdfOPpo5vHKFCh8Ypr44O1rEJR2DpU+mf/2Q=="
}