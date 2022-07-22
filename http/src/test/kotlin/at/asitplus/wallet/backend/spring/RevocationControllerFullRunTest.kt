package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.RevocationController
import at.asitplus.wallet.backend.TestTimeSource
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.backend.javatimePeriod
import at.asitplus.wallet.lib.agent.IssuerAgent
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Year
import java.util.*
import kotlin.time.Duration.Companion.seconds

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(authorities = ["REVOCATION"])
@TestPropertySource(properties = ["backend.time-source=TEST"])
class RevocationControllerFullRunTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @Autowired
    private lateinit var deviceBindingRepository: DeviceBindingRepository

    @Autowired
    private lateinit var credentialRepo: IssuedCredentialRepository

    private lateinit var bpk: String
    private lateinit var client: Client
    private lateinit var certificate: ByteArray
    private lateinit var vcId: String
    private lateinit var attributeName: String
    private lateinit var subjectId: String
    private lateinit var validUntil: Instant
    private lateinit var deviceBinding: DeviceBinding
    private lateinit var deviceId: String
    private lateinit var deviceName: String

    @BeforeEach
    fun beforeEach() {
        bpk = UUID.randomUUID().toString()
        client = Client()
        certificate = client.selfSignedCert.encoded
        vcId = UUID.randomUUID().toString()
        attributeName = UUID.randomUUID().toString()
        subjectId = UUID.randomUUID().toString()
        validUntil = TestTimeSource.now() + 5.seconds
        deviceBindingRepository.deleteAll()
        deviceBinding = client.storeDeviceBinding(bpk, deviceBindingRepository)
        deviceId = deviceBinding.deviceId
        deviceName = deviceBinding.deviceName
        credentialRepo.deleteAll()
        IssuedCredential(
            vcId,
            subjectId,
            validUntil.toJavaInstant(),
            TestTimeSource.javatimePeriod,
            deviceBinding,
            attributeName,
            2
        )
            .also { credentialRepo.save(it) }
    }

    @Test
    fun `revoking binding leads to revoked credential`() {
        credentialRepo.findAllByRevokedFalseAndValidUntilAfter(TestTimeSource.now().toJavaInstant())
            .shouldNotBeEmpty()
        deviceBindingRepository.findAllByRevokedFalseAndValidUntilAfter(
            TestTimeSource.now().toJavaInstant()
        ).shouldNotBeEmpty()

        val request = RevocationController.RevocationRequest(bpk)
        val expectedResponse = RevocationController.RevocationResponse(1)

        mockMvc.post("/revoke/binding") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { json(mapper.writeValueAsString(expectedResponse)) }
        }.andReturn()

        credentialRepo.findAllByRevokedFalseAndValidUntilAfter(TestTimeSource.now().toJavaInstant())
            .shouldBeEmpty()
        deviceBindingRepository.findAllByRevokedFalseAndValidUntilAfter(
            TestTimeSource.now().toJavaInstant()
        ).shouldBeEmpty()
    }

    @Test
    fun `expired binding cannot be revoked`() {
        val validUntil = TestTimeSource.now() - 1.seconds
        saveExpiredBinding(validUntil)

        val request = RevocationController.RevocationRequest(bpk)

        mockMvc.post("/revoke/binding") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isNotFound() }
        }.andReturn()
    }

    @Test
    fun `revoking binding by bpk and deviceId leads to revoked credential`() {
        credentialRepo.findAllByRevokedFalseAndValidUntilAfter(TestTimeSource.now().toJavaInstant())
            .shouldNotBeEmpty()
        deviceBindingRepository.findAllByRevokedFalseAndValidUntilAfter(
            TestTimeSource.now().toJavaInstant()
        ).shouldNotBeEmpty()
        val request = RevocationController.RevocationRequest(bpk, deviceId)
        val expectedResponse = RevocationController.RevocationResponse(1)

        mockMvc.post("/revoke/binding") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { json(mapper.writeValueAsString(expectedResponse)) }
        }.andReturn()

        credentialRepo.findAllByRevokedFalseAndValidUntilAfter(TestTimeSource.now().toJavaInstant())
            .shouldBeEmpty()
        deviceBindingRepository.findAllByRevokedFalseAndValidUntilAfter(
            TestTimeSource.now().toJavaInstant()
        ).shouldBeEmpty()
    }

    @Test
    fun `revoking pupilId by bpk does not lead to revoked binding`() {
        credentialRepo.findAllByRevokedFalseAndValidUntilAfter(TestTimeSource.now().toJavaInstant())
            .shouldNotBeEmpty()
        deviceBindingRepository.findAllByRevokedFalseAndValidUntilAfter(
            TestTimeSource.now().toJavaInstant()
        ).shouldNotBeEmpty()

        val request = RevocationController.RevocationRequest(bpk)
        val expectedResponse = RevocationController.RevocationResponse(1)

        mockMvc.post("/revoke/pupilid") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { json(mapper.writeValueAsString(expectedResponse)) }
        }.andReturn()

        credentialRepo.findAllByRevokedFalseAndValidUntilAfter(TestTimeSource.now().toJavaInstant())
            .shouldBeEmpty()
        deviceBindingRepository.findAllByRevokedFalseAndValidUntilAfter(
            TestTimeSource.now().toJavaInstant()
        ).shouldNotBeEmpty()
    }

    @Test
    fun `expired pupilId can not be revoked`() {
        val validUntil = TestTimeSource.now() - 1.seconds
        val deviceBinding = saveExpiredBinding(validUntil)
        saveExpiredCredential(validUntil, deviceBinding)
        val request = RevocationController.RevocationRequest(bpk)

        mockMvc.post("/revoke/pupilid") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isNotFound() }
        }.andReturn()
    }

    @Test
    fun `active bindings are listed as devices`() {
        val expectedResponse = RevocationController.DeviceListResponse(
            listOf(RevocationController.DeviceListResponseEntry(deviceId, deviceName))
        )

        mockMvc.get("/revoke/devices?bpk=$bpk") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            content { json(mapper.writeValueAsString(expectedResponse)) }
        }.andReturn()
    }

    @Test
    fun `expired bindings are not listed as devices`() {
        val validUntil = TestTimeSource.now() - 1.seconds
        val deviceBinding = saveExpiredBinding(validUntil)
        saveExpiredCredential(validUntil, deviceBinding)

        mockMvc.get("/revoke/devices?bpk=$bpk") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
        }.andReturn()
    }

    private fun saveExpiredBinding(validUntil: Instant): DeviceBinding {
        deviceBindingRepository.deleteAll()
        val deviceBinding =
            DeviceBinding(
                bpk,
                client.selfSignedCert.encoded,
                deviceName,
                deviceId,
                validUntil.toJavaInstant()
            )
                .also { deviceBindingRepository.save(it) }
        return deviceBinding
    }

    private fun saveExpiredCredential(validUntil: Instant, deviceBinding: DeviceBinding) {
        credentialRepo.deleteAll()
        IssuedCredential(
            vcId,
            subjectId,
            validUntil.toJavaInstant(),
            TestTimeSource.javatimePeriod,
            deviceBinding,
            attributeName,
            3
        )
            .also { credentialRepo.save(it) }
    }

}