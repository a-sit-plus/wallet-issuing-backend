package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.wallet.backend.config.EcoAttributeSourceConfigurationProperties
import at.asitplus.wallet.backend.config.HttpBasicAuthnConfigurationProperties
import at.asitplus.wallet.backend.service.RestTemplateConfigurationService
import at.asitplus.wallet.lib.DataSourceProblem
import at.asitplus.wallet.pupilid.ConstantIndex
import at.asitplus.wallet.pupilid.PupilIdCredential
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.matthewnelson.component.base64.encodeBase64
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.web.client.RestTemplate
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EcoDataProviderTest {

    @Autowired
    private lateinit var restTemplateBuilder: RestTemplateBuilder

    private lateinit var server: MockWebServer
    private lateinit var url: HttpUrl
    private lateinit var restTemplate: RestTemplate
    private lateinit var config: EcoAttributeSourceConfigurationProperties

    @BeforeAll
    fun setup() {
        server = MockWebServer().apply {
            start()
            url = url("/fakeEco")
        }
        config = EcoAttributeSourceConfigurationProperties(
            url.toUri(),
            serverTls = false,
            httpBasic = HttpBasicAuthnConfigurationProperties("baz", "foo")
        )
        restTemplate = RestTemplateConfigurationService(
            config,
            restTemplateBuilder
        ).restTemplate
    }

    @Test
    fun wrongPictureFormat() {
        val expiryFromEco = Clock.System.now()
        val fakeStudentData = """{
            "validUntil": """" + expiryFromEco + """",
            "cardId": "string",
            "firstname": "string",
            "lastname": "string",
            "dateOfBirth": "2022-12-20T11:01:41.609Z",
            "schoolName": "string",
            "schoolCity": "string",
            "schoolZip": "string",
            "schoolStreet": "string",
            "schoolId": "string",
            "studentCity": "string",
            "studentZip": "string",
            "photo": "foo"
        }""".trimIndent()
        server.enqueue(MockResponse().addHeader("Content-Type", "application/json").setBody(fakeStudentData))
        val eco = setupEcoDateProvider(0.seconds)
        val cred = eco.getCredential(
            "foo",
            ConstantIndex.PupilId.vcType,
            "bpk",
            maxExpiration = Clock.System.now() + 20000.days
        )
        cred.isFailure shouldBe true
        cred.exceptionOrNull()!!.shouldBeInstanceOf<DataSourceProblem>()
    }

    @Test
    fun basicOK() {
        val expiryFromEco = Clock.System.now()
        val (vc, pupilIdCredential) = testWithDates(
            validUntilFromEco = expiryFromEco,
            gracePeriod = 0.seconds,
            maxExpirationCappedFromCredentialValidityAndBinding = Clock.System.now() + 20000.days
        )

        vc shouldExpireAt expiryFromEco
        pupilIdCredential shouldBeValidUntil expiryFromEco
    }

    @Test
    fun graceWithinCappedValidity() {
        val expiryFromEco = Clock.System.now()
        val gracePeriod = 10.days
        val (vc, pupilIdCredential) = testWithDates(
            validUntilFromEco = expiryFromEco,
            gracePeriod = gracePeriod,
            maxExpirationCappedFromCredentialValidityAndBinding = Clock.System.now() + 20000.days
        )

        vc shouldExpireAt expiryFromEco + gracePeriod
        pupilIdCredential shouldBeValidUntil expiryFromEco
    }

    @Test
    fun gracePastCappedValidity() {
        val expiryFromEco = Clock.System.now()
        val gracePeriod = 20.days
        val cappedExpiration = expiryFromEco + 10.days
        val (vc, pupilIdCredential) = testWithDates(
            validUntilFromEco = expiryFromEco,
            gracePeriod = gracePeriod,
            maxExpirationCappedFromCredentialValidityAndBinding = cappedExpiration
        )

        vc shouldExpireAt cappedExpiration
        pupilIdCredential shouldBeValidUntil expiryFromEco
    }


    @Test
    fun ecoAndGracePastCappedValidity() {
        val now = Clock.System.now()
        val expiryFromEco = now + 30.days
        val gracePeriod = 20.days
        val cappedExpiration = now + 10.days
        val (vc, pupilIdCredential) = testWithDates(
            validUntilFromEco = expiryFromEco,
            gracePeriod = gracePeriod,
            maxExpirationCappedFromCredentialValidityAndBinding = cappedExpiration
        )

        vc shouldExpireAt cappedExpiration
        pupilIdCredential shouldBeValidUntil cappedExpiration
    }


    @Test
    fun ecoPlusPastCappedValidity() {
        val now = Clock.System.now()
        val expiryFromEco = now + 10.days
        val gracePeriod = 20.days
        val cappedExpiration = now + 20.days
        val (vc, pupilIdCredential) = testWithDates(
            validUntilFromEco = expiryFromEco,
            gracePeriod = gracePeriod,
            maxExpirationCappedFromCredentialValidityAndBinding = cappedExpiration
        )

        vc shouldExpireAt cappedExpiration
        pupilIdCredential shouldBeValidUntil expiryFromEco
    }


    private fun testWithDates(
        validUntilFromEco: Instant,
        gracePeriod: Duration,
        maxExpirationCappedFromCredentialValidityAndBinding: Instant
    ): Pair<CredentialDataProvider.CredentialToBeIssued, PupilIdCredential> {
        val eco = setupEcoDateProvider(gracePeriod)
        prepareEcoResponse(validUntil = validUntilFromEco)
        val cred = eco.getCredential(
            "foo",
            ConstantIndex.PupilId.vcType,
            "bpk",
            maxExpiration = maxExpirationCappedFromCredentialValidityAndBinding
        )
        cred.isSuccess.shouldBeTrue()
        cred as KmmResult.Success
        val sub = cred.value.subject
        sub.shouldBeInstanceOf<PupilIdCredential>()

        return cred.value to sub
    }


    @Suppress("NOTHING_TO_INLINE")
    private inline infix fun CredentialDataProvider.CredentialToBeIssued.shouldExpireAt(instant: Instant) =
        expiration shouldBe instant

    @Suppress("NOTHING_TO_INLINE")
    private inline infix fun PupilIdCredential.shouldBeValidUntil(instant: Instant) =
        validUntil shouldBe instant.toString().substring(0 until 10)

    private fun setupEcoDateProvider(gracePeriod: Duration): EcoCredentialDataProvider =
        EcoCredentialDataProvider(config.url.toString(), restTemplate, gracePeriod, NoopPictureService)

    private fun prepareEcoResponse(validUntil: Instant) {
        val fakeStudentData = """{
            "validUntil": """" + validUntil + """",
            "cardId": "string",
            "firstname": "string",
            "lastname": "string",
            "dateOfBirth": "2022-12-20T11:01:41.609Z",
            "schoolName": "string",
            "schoolCity": "string",
            "schoolZip": "string",
            "schoolStreet": "string",
            "schoolId": "string",
            "studentCity": "string",
            "studentZip": "string",
            "photo": """" + File("src/test/resources/portrait.jpeg").readBytes().encodeBase64() + """"
        }""".trimIndent()
        server.enqueue(MockResponse().addHeader("Content-Type", "application/json").setBody(fakeStudentData))
    }

}