package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.model.IdentifierRegistry
import at.asitplus.wallet.backend.model.IdentifierRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


@SpringBootTest
@AutoConfigureTestDatabase
class IdentifierRegistryTest {

    @Autowired
    lateinit var identifierRegistry: IdentifierRegistry

    @Autowired
    lateinit var identifierRepository: IdentifierRepository

    private lateinit var vcId: String
    private lateinit var attributeName: String
    private lateinit var subjectId: String

    @BeforeEach
    fun beforeEach() {
        vcId = UUID.randomUUID().toString()
        attributeName = UUID.randomUUID().toString()
        subjectId = UUID.randomUUID().toString()
        identifierRepository.deleteAll()
    }

    @Test
    fun `revocation of non-existing vcId should do nothing`() {
        assertFalse(identifierRegistry.revoke(vcId))
    }

    @Test
    fun `check on non-existing vcId should return null`() {
        assertNull(identifierRegistry.isRevoked(vcId))
    }

    @Test
    fun `simple positive add and revoke vcId should work`() {
        identifierRegistry.storeGetNextIndex(vcId, attributeName, subjectId)
        assertEquals(false, identifierRegistry.isRevoked(vcId))
        assertTrue(identifierRegistry.revoke(vcId))
        assertEquals(true, identifierRegistry.isRevoked(vcId))
    }

    @Test
    fun `double adding vcId should return null`() {
        assertNotNull(identifierRegistry.storeGetNextIndex(vcId,attributeName, subjectId))
        assertNull(identifierRegistry.storeGetNextIndex(vcId, attributeName, subjectId))
    }

    @Test
    fun `revocation list should match revocation calls`() {
        val expectedRevocationList = revokeRandomCredentials()

        val revocationList = identifierRegistry.getRevokedStatusListIndexList()
        assertContentEquals(expectedRevocationList, revocationList, "Revocation list should match revocation calls")
    }

    private fun revokeRandomCredentials(): MutableList<Int> {
        val expectedRevocationList = mutableListOf<Int>()
        for (i in 1..256) {
            val vcId = UUID.randomUUID().toString()
            val revocationListIndex = identifierRegistry.storeGetNextIndex(vcId, attributeName, subjectId)
            if (Random.nextBoolean()) {
                expectedRevocationList.add(revocationListIndex!!)
                identifierRegistry.revoke(vcId)
            }
        }
        return expectedRevocationList
    }

}