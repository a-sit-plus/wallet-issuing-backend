package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.model.IdentifierRegistry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext

@SpringBootTest
@AutoConfigureTestDatabase
class IdentifierRegistryTest {

    @Autowired
    lateinit var identifierRegistry: IdentifierRegistry

    private val key: String = "2q4t09j0qj09fj́w09"

    @Test
    fun `revocation of non existing key should throw exception`() {
        Assertions.assertThrows(Exception::class.java, {
            identifierRegistry.isRevoked(key)
        }, "Can not revoke what is not there (isn't registered)")
            .also { assertEquals(it.message, "Not registered", "Wrong Exception Text") }
    }

    @Test
    fun `check on non existing key should throw an exception`() {
        Assertions.assertThrows(Exception::class.java, {
            identifierRegistry.isRevoked(key)
        }, "Can not check what is not there (isn't registered)")
            .also { assertEquals(it.message, "Not registered", "Wrong Exception Text") }
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `simple positive add and revoke key should work`() {
        identifierRegistry.addIdentifier(key)
        identifierRegistry.isRevoked(key).also { assertEquals(false, it, "key is already revoked") }
        identifierRegistry.revoke(key)
        identifierRegistry.isRevoked(key).also { assertEquals(true, it, "Should be revoked but isn't") }
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `double adding key should throw exception`() {
        Assertions.assertThrows(Exception::class.java, {
            identifierRegistry.addIdentifier(key)
            identifierRegistry.addIdentifier(key)
        }, "Double ID registration possible")
            .also { assertEquals(it.message, "Already registered", "Wrong Exception Text") }
    }

}