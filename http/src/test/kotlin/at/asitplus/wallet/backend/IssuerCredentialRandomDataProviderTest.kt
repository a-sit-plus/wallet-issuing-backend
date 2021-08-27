package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.data.PupilIdAttributes
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IssuerCredentialRandomDataProviderTest {

    @Test
    fun `claims should be different on successive calls`() {
        val listOfPhotos = (1..10).associate { UUID.randomUUID().toString() to UUID.randomUUID().toString() }
        val subjectId = UUID.randomUUID().toString() // each subject has own attribute set
        val subjectId2 = UUID.randomUUID().toString()
        val firstSetOfValues = mutableListOf<String>()
        val secondSetOfValues = mutableListOf<String>()
        val dataProvider = IssuerCredentialRandomDataProvider(Duration.ofSeconds(1), listOfPhotos)
        for (attribute in PupilIdAttributes.listOfAttributes) {

            val firstClaim = dataProvider.getClaim(subjectId, attribute)
            assertNotNull(firstClaim)
            assertEquals(attribute, firstClaim.name)
            assertTrue(firstClaim.value.isNotEmpty())

            val secondClaim = dataProvider.getClaim(subjectId2, attribute)
            assertNotNull(secondClaim)
            assertEquals(attribute, secondClaim.name)
            assertTrue(secondClaim.value.isNotEmpty())

            firstSetOfValues += firstClaim.value
            secondSetOfValues += secondClaim.value
        }
        assertNotEquals(firstSetOfValues, secondSetOfValues)
    }

}