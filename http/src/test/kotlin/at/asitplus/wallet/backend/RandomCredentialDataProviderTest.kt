package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.InMemoryDeviceBindingStorageService
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.lib.data.AtomicAttributeCredential
import at.asitplus.wallet.lib.data.AttributeIndex
import java.util.UUID
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RandomCredentialDataProviderTest {

    private lateinit var subjectId1: String
    private lateinit var subjectId2: String
    private lateinit var dataProvider: RandomCredentialDataProvider
    private lateinit var deviceBindingStorageService: DeviceBindingStorageService

    @BeforeTest
    fun setup() {
        val listOfPhotos = (1..10).associate { UUID.randomUUID().toString() to Random.Default.nextBytes(32) }
        subjectId1 = UUID.randomUUID().toString() // each subject has own attribute set
        subjectId2 = UUID.randomUUID().toString()
        deviceBindingStorageService = InMemoryDeviceBindingStorageService().also {
            it.setDeviceBindingForCurrentUser(DeviceBinding("bpk", byteArrayOf(), "", ""))
        }
        dataProvider = RandomCredentialDataProvider(1.seconds, listOfPhotos, deviceBindingStorageService)
    }

    @Test
    fun `claims for pupil id should be different on successive calls`() {
        val firstSetOfValues = mutableListOf<String>()
        val secondSetOfValues = mutableListOf<String>()
        for (attribute in AttributeIndex.genericAttributes) {
            dataProvider.getClaim(subjectId1, attribute).let {
                assertIs<AtomicAttributeCredential>(it)
                assertClaim(it, attribute)
                firstSetOfValues += it.value
            }
            dataProvider.getClaim(subjectId2, attribute).let {
                assertIs<AtomicAttributeCredential>(it)
                assertClaim(it, attribute)
                secondSetOfValues += it.value
            }
        }
        assertNotEquals(firstSetOfValues, secondSetOfValues)
    }

    @Test
    fun `claims for green pass should be different on successive calls`() {
        val firstSetOfValues = mutableListOf<String>()
        val secondSetOfValues = mutableListOf<String>()
        for (attribute in AttributeIndex.greenPassAttributes) {
            dataProvider.getClaim(subjectId1, attribute).let {
                assertIs<AtomicAttributeCredential>(it)
                assertClaim(it, attribute)
                firstSetOfValues += it.value
            }
            dataProvider.getClaim(subjectId2, attribute).let {
                assertIs<AtomicAttributeCredential>(it)
                assertClaim(it, attribute)
                secondSetOfValues += it.value
            }
        }
        assertNotEquals(firstSetOfValues, secondSetOfValues)
    }

    private fun assertClaim(firstClaim: AtomicAttributeCredential?, attribute: String) {
        assertNotNull(firstClaim)
        assertEquals(attribute, firstClaim.name)
        assertTrue(firstClaim.value.isNotEmpty())
    }

}