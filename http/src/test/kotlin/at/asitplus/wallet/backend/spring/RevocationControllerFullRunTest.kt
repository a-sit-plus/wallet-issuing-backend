package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.backend.Client
import at.asitplus.wallet.backend.RevocationController
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(authorities = ["REVOCATION"])
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
    private lateinit var deviceName: String
    private lateinit var deviceId: String
    private lateinit var certificate: ByteArray
    private lateinit var vcId: String
    private lateinit var attributeName: String
    private lateinit var attributeValue: String
    private lateinit var subjectId: String
    private lateinit var validUntil: Instant
    private lateinit var deviceBinding: DeviceBinding

    @BeforeEach
    fun beforeEach() {
        bpk = UUID.randomUUID().toString()
        deviceName = UUID.randomUUID().toString()
        deviceId = UUID.randomUUID().toString()
        certificate = Client().selfSignedCert.encoded
        vcId = UUID.randomUUID().toString()
        attributeName = UUID.randomUUID().toString()
        attributeValue = UUID.randomUUID().toString()
        subjectId = UUID.randomUUID().toString()
        validUntil = Instant.now().plusSeconds(5)
        deviceBinding = DeviceBinding(bpk, certificate, deviceName, deviceId, validUntil)
        deviceBindingRepository.deleteAll()
        credentialRepo.deleteAll()
        deviceBinding = deviceBindingRepository.save(deviceBinding)
        IssuedCredential(vcId, subjectId, validUntil, deviceBinding, attributeName, 2).also {
            credentialRepo.save(it)
        }
    }

    @Test
    fun `revoking binding leads to revoked credential`() {
        credentialRepo.findAllByRevokedFalse().shouldNotBeEmpty()
        deviceBindingRepository.findAllByRevokedFalse().shouldNotBeEmpty()

        val request = RevocationController.RevocationRequest(bpk)
        val expectedResponse = RevocationController.RevocationResponse(1)

        mockMvc.post("/revoke/binding") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { json(mapper.writeValueAsString(expectedResponse)) }
        }.andReturn()

        credentialRepo.findAllByRevokedFalse().shouldBeEmpty()
        deviceBindingRepository.findAllByRevokedFalse().shouldBeEmpty()
    }

    @Test
    fun `revoking binding by bpk and deviceId leads to revoked credential`() {
        credentialRepo.findAllByRevokedFalse().shouldNotBeEmpty()
        deviceBindingRepository.findAllByRevokedFalse().shouldNotBeEmpty()
        val request = RevocationController.RevocationRequest(bpk, deviceId)
        val expectedResponse = RevocationController.RevocationResponse(1)

        mockMvc.post("/revoke/binding") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { json(mapper.writeValueAsString(expectedResponse)) }
        }.andReturn()


        credentialRepo.findAllByRevokedFalse().shouldBeEmpty()
        deviceBindingRepository.findAllByRevokedFalse().shouldBeEmpty()
    }

    @Test
    fun `revoking pupilId by bpk does not lead to revoked binding`() {
        credentialRepo.findAllByRevokedFalse().shouldNotBeEmpty()
        deviceBindingRepository.findAllByRevokedFalse().shouldNotBeEmpty()

        val request = RevocationController.RevocationRequest(bpk)
        val expectedResponse = RevocationController.RevocationResponse(1)

        mockMvc.post("/revoke/pupilid") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { json(mapper.writeValueAsString(expectedResponse)) }
        }.andReturn()

        credentialRepo.findAllByRevokedFalse().shouldBeEmpty()
        deviceBindingRepository.findAllByRevokedFalse().shouldNotBeEmpty()
    }

}