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
        val fallbackPhoto = UUID.randomUUID().toString()
        val subjectId = UUID.randomUUID().toString()
        val attribute = PupilIdAttributes.listOfAttributes.random()
        val dataProvider = IssuerCredentialRandomDataProvider(Duration.ofSeconds(1), fallbackPhoto)

        val firstClaim = dataProvider.getClaim(subjectId, attribute)
        assertNotNull(firstClaim)
        assertEquals(attribute, firstClaim.name)
        assertTrue(firstClaim.value.isNotEmpty())

        val secondClaim = dataProvider.getClaim(subjectId, attribute)
        assertNotNull(secondClaim)
        assertEquals(attribute, secondClaim.name)
        assertTrue(secondClaim.value.isNotEmpty())

        assertNotEquals(firstClaim.value, secondClaim.value)

    }

}