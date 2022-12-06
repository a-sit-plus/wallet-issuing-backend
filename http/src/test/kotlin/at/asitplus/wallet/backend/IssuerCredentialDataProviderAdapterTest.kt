package at.asitplus.wallet.backend

import at.asitplus.KmmResult
import at.asitplus.wallet.backend.auth.InMemoryDeviceBindingStorageService
import at.asitplus.wallet.backend.data.CredentialDataProvider
import at.asitplus.wallet.backend.data.IssuerCredentialDataProviderAdapter
import at.asitplus.wallet.backend.data.RandomCredentialDataProvider
import at.asitplus.wallet.backend.service.DeviceBindingStorageService
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.SchemaIndex.ATTR_GENERIC_PREFIX
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.datetime.toKotlinInstant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.properties.Delegates
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class IssuerCredentialDataProviderAdapterTest {

    private lateinit var credentialDataProvider: CredentialDataProvider
    private lateinit var deviceBindingStorageService: DeviceBindingStorageService
    private lateinit var subjectId: String
    private lateinit var deviceName: String
    private lateinit var bpk: String
    private lateinit var client: Client
    private var lifetime: Duration by Delegates.notNull()
    private lateinit var adapter: IssuerCredentialDataProviderAdapter

    @BeforeEach
    fun setup() {
        credentialDataProvider = RandomCredentialDataProvider(mapOf())
        deviceBindingStorageService = InMemoryDeviceBindingStorageService()
        subjectId = UUID.randomUUID().toString()
        deviceName = UUID.randomUUID().toString()
        bpk = UUID.randomUUID().toString()
        client = Client()
        deviceBindingStorageService.store(
            bpk,
            client.selfSignedCert.encoded,
            deviceName,
            client.selfSignedCert.notAfter.toInstant().toKotlinInstant()
        )
        lifetime = client.lifetimeSeconds.seconds
        adapter = IssuerCredentialDataProviderAdapter(
            lifetime,
            credentialDataProvider,
            deviceBindingStorageService
        )
    }

    @Test
    fun `credential lifetime should be capped with binding lifetime`() {
        val credential = adapter.getCredential(client.keyId, ConstantIndex.PupilId.vcType)

        credential.isSuccess shouldBe true
        credential as KmmResult.Success
        credential.value.expiration shouldBeLessThanOrEqualTo client.selfSignedCert.notAfter.toInstant()
            .toKotlinInstant()
    }

    @Test
    fun `claim lifetime should be capped with binding lifetime`() {
        val credential = adapter.getClaim(client.keyId, "$ATTR_GENERIC_PREFIX/given-name")

        credential.isSuccess shouldBe true
        credential as KmmResult.Success
        credential.value.expiration shouldBeLessThanOrEqualTo client.selfSignedCert.notAfter.toInstant()
            .toKotlinInstant()
    }

}
