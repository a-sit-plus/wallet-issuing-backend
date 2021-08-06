package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.model.IdentifierRegistry
import at.asitplus.wallet.backend.model.IdentifierRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertContentEquals


@SpringBootTest
@AutoConfigureTestDatabase
class IdentifierRegistryTest {

    @Autowired
    lateinit var identifierRegistry: IdentifierRegistry

    @Autowired
    lateinit var identifierRepository: IdentifierRepository

    private lateinit var vcId: String

    @BeforeEach
    fun beforeEach() {
        vcId = UUID.randomUUID().toString()
        identifierRepository.deleteAll()
    }

    @Test
    fun `revocation of non existing key should throw exception`() {
        Assertions.assertThrows(Exception::class.java, {
            identifierRegistry.isRevoked(vcId)
        }, "Can not revoke what is not there (isn't registered)")
            .also { assertEquals(it.message, "Not registered", "Wrong Exception Text") }
    }

    @Test
    fun `check on non existing key should throw an exception`() {
        Assertions.assertThrows(Exception::class.java, {
            identifierRegistry.isRevoked(vcId)
        }, "Can not check what is not there (isn't registered)")
            .also { assertEquals(it.message, "Not registered", "Wrong Exception Text") }
    }

    @Test
    fun `simple positive add and revoke key should work`() {
        identifierRegistry.storeGetNextIndex(vcId)
        identifierRegistry.isRevoked(vcId).also { assertEquals(false, it, "key is already revoked") }
        identifierRegistry.revoke(vcId)
        identifierRegistry.isRevoked(vcId).also { assertEquals(true, it, "Should be revoked but isn't") }
    }

    @Test
    fun `double adding key should throw exception`() {
        Assertions.assertThrows(Exception::class.java, {
            identifierRegistry.storeGetNextIndex(vcId)
            identifierRegistry.storeGetNextIndex(vcId)
        }, "Double ID registration possible")
            .also { assertEquals(it.message, "Already registered", "Wrong Exception Text") }
    }

    @Test
    fun `revocation list should match revocation calls`() {
        val toBeRevokedList = mutableMapOf<Int, String>()
        for (i in 0..9) {
            val key = UUID.randomUUID().toString()
            val revocationListIndex = identifierRegistry.storeGetNextIndex(key)
            if (Random.nextBoolean()) {
                toBeRevokedList[revocationListIndex] = key
            }
        }
        toBeRevokedList.forEach { identifierRegistry.revoke(it.value) }
        val expectedRevocationList = toBeRevokedList.map { it.key }
        val revocationList = identifierRegistry.getRevokedStatusListIndexList()
        assertContentEquals(expectedRevocationList, revocationList, "Revocation list should match revocation calls")
    }

}