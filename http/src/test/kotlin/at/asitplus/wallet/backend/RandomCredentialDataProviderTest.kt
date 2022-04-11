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

class RandomCredentialDataProviderTest {

    private lateinit var subjectId1: String
    private lateinit var subjectId2: String
    private lateinit var bpk1: String
    private lateinit var bpk2: String
    private lateinit var dataProvider: RandomCredentialDataProvider
    private lateinit var deviceBindingStorageService: InMemoryDeviceBindingStorageService

    @BeforeTest
    fun setup() {
        val listOfPhotos = (1..10).associate { UUID.randomUUID().toString() to Random.Default.nextBytes(32) }
        bpk1 = UUID.randomUUID().toString()
        bpk2 = UUID.randomUUID().toString()
        subjectId1 = UUID.randomUUID().toString() // each subject has own attribute set
        subjectId2 = UUID.randomUUID().toString()
        deviceBindingStorageService = InMemoryDeviceBindingStorageService().also {
            it.setDeviceBindingForCurrentUser(DeviceBinding(bpk1, byteArrayOf(), "", ""))
        }
        dataProvider = RandomCredentialDataProvider(listOfPhotos, deviceBindingStorageService)
    }

    @Test
    fun `claims for different bpks should be different on successive calls`() {
        val firstSetOfValues = mutableListOf<String>()
        val secondSetOfValues = mutableListOf<String>()
        for (attribute in AttributeIndex.genericAttributes) {
            deviceBindingStorageService.setDeviceBindingForCurrentUser(DeviceBinding(bpk1, byteArrayOf(), "", ""))
            dataProvider.getClaim(subjectId1, attribute, bpk1).let {
                assertIs<AtomicAttributeCredential>(it)
                assertClaim(it, attribute)
                firstSetOfValues += it.value
            }
            deviceBindingStorageService.setDeviceBindingForCurrentUser(DeviceBinding(bpk2, byteArrayOf(), "", ""))
            dataProvider.getClaim(subjectId2, attribute, bpk2).let {
                assertIs<AtomicAttributeCredential>(it)
                assertClaim(it, attribute)
                secondSetOfValues += it.value
            }
        }
        assertNotEquals(firstSetOfValues, secondSetOfValues)
    }

    @Test
    fun `claims for the same bpk should be the same on successive calls`() {
        val firstSetOfValues = mutableListOf<String>()
        val secondSetOfValues = mutableListOf<String>()
        for (attribute in AttributeIndex.genericAttributes) {
            dataProvider.getClaim(subjectId1, attribute, bpk1).let {
                assertIs<AtomicAttributeCredential>(it)
                assertClaim(it, attribute)
                firstSetOfValues += it.value
            }
            dataProvider.getClaim(subjectId2, attribute, bpk1).let {
                assertIs<AtomicAttributeCredential>(it)
                assertClaim(it, attribute)
                secondSetOfValues += it.value
            }
        }
        assertEquals(firstSetOfValues, secondSetOfValues)
    }

    private fun assertClaim(firstClaim: AtomicAttributeCredential?, attribute: String) {
        assertNotNull(firstClaim)
        assertEquals(attribute, firstClaim.name)
        assertTrue(firstClaim.value.isNotEmpty())
    }

}