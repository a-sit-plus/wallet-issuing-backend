package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.lib.data.AtomicAttributeCredential
import at.asitplus.wallet.lib.data.AttributeIndex
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

class RandomCredentialDataProviderTest {

    private lateinit var subjectId1: String
    private lateinit var subjectId2: String
    private lateinit var bpk1: String
    private lateinit var bpk2: String
    private lateinit var dataProvider: RandomCredentialDataProvider
    private lateinit var deviceBindingStorageService: DeviceBindingStorageService

    @BeforeEach
    fun setup() {
        val listOfPhotos = (1..10).associate { UUID.randomUUID().toString() to Random.Default.nextBytes(32) }
        bpk1 = UUID.randomUUID().toString()
        bpk2 = UUID.randomUUID().toString()
        subjectId1 = UUID.randomUUID().toString() // each subject has own attribute set
        subjectId2 = UUID.randomUUID().toString()
        deviceBindingStorageService = mock()
        whenever(deviceBindingStorageService.getDeviceBindingForCurrentUser())
            .thenReturn(DeviceBinding(bpk1, byteArrayOf(), "", "", Instant.now()))
        dataProvider = RandomCredentialDataProvider(listOfPhotos)
    }

    @Test
    fun `claims for different bpks should be different on successive calls`() {
        val firstSetOfValues = mutableListOf<String>()
        val secondSetOfValues = mutableListOf<String>()
        for (attribute in AttributeIndex.genericAttributes) {
            whenever(deviceBindingStorageService.getDeviceBindingForCurrentUser())
                .thenReturn(DeviceBinding(bpk1, byteArrayOf(), "", "", Instant.now()))
            dataProvider.getClaim(subjectId1, attribute, bpk1).let {
                it.shouldBeInstanceOf<AtomicAttributeCredential>()
                assertClaim(it, attribute)
                firstSetOfValues += it.value
            }
            whenever(deviceBindingStorageService.getDeviceBindingForCurrentUser())
                .thenReturn(DeviceBinding(bpk2, byteArrayOf(), "", "", Instant.now()))
            dataProvider.getClaim(subjectId2, attribute, bpk2).let {
                it.shouldBeInstanceOf<AtomicAttributeCredential>()
                assertClaim(it, attribute)
                secondSetOfValues += it.value
            }
        }
        firstSetOfValues shouldNotBe secondSetOfValues
    }

    @Test
    fun `claims for the same bpk should be the same on successive calls`() {
        val firstSetOfValues = mutableListOf<String>()
        val secondSetOfValues = mutableListOf<String>()
        for (attribute in AttributeIndex.genericAttributes) {
            dataProvider.getClaim(subjectId1, attribute, bpk1).let {
                it.shouldBeInstanceOf<AtomicAttributeCredential>()
                assertClaim(it, attribute)
                firstSetOfValues += it.value
            }
            dataProvider.getClaim(subjectId2, attribute, bpk1).let {
                it.shouldBeInstanceOf<AtomicAttributeCredential>()
                assertClaim(it, attribute)
                secondSetOfValues += it.value
            }
        }
        firstSetOfValues shouldBe secondSetOfValues
    }

    private fun assertClaim(firstClaim: AtomicAttributeCredential?, attribute: String) {
        firstClaim.shouldNotBeNull()
        firstClaim.name shouldBe attribute
        firstClaim.value.shouldNotBeEmpty()
    }

}