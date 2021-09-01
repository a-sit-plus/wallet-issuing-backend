package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.Claim
import at.asitplus.wallet.lib.data.AttributeIndex
import java.time.Duration
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IssuerCredentialRandomDataProviderTest {

    private lateinit var subjectId1: String
    private lateinit var subjectId2: String
    private lateinit var dataProvider: IssuerCredentialRandomDataProvider

    @BeforeTest
    fun setup() {
        val listOfPhotos = (1..10).associate { UUID.randomUUID().toString() to UUID.randomUUID().toString() }
        subjectId1 = UUID.randomUUID().toString() // each subject has own attribute set
        subjectId2 = UUID.randomUUID().toString()
        dataProvider = IssuerCredentialRandomDataProvider(Duration.ofSeconds(1), listOfPhotos)
    }

    @Test
    fun `claims for pupil id should be different on successive calls`() {
        val firstSetOfValues = mutableListOf<String>()
        val secondSetOfValues = mutableListOf<String>()
        for (attribute in AttributeIndex.pupilIdAttributes) {
            dataProvider.getClaim(subjectId1, attribute).let {
                assertClaim(it, attribute)
                firstSetOfValues += it!!.value
            }
            dataProvider.getClaim(subjectId2, attribute).let {
                assertClaim(it, attribute)
                secondSetOfValues += it!!.value
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
                assertClaim(it, attribute)
                firstSetOfValues += it!!.value
            }
            dataProvider.getClaim(subjectId2, attribute).let {
                assertClaim(it, attribute)
                secondSetOfValues += it!!.value
            }
        }
        assertNotEquals(firstSetOfValues, secondSetOfValues)
    }

    private fun assertClaim(firstClaim: Claim?, attribute: String) {
        assertNotNull(firstClaim)
        assertEquals(attribute, firstClaim.name)
        assertTrue(firstClaim.value.isNotEmpty())
    }

}