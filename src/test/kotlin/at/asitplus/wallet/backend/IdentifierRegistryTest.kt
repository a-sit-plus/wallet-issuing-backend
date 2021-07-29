package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.data.Agent
import at.asitplus.wallet.backend.data.VerifiableCredentialSerialized
import at.asitplus.wallet.backend.model.IdentifierRegistry
import org.assertj.core.api.JUnitSoftAssertions
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.function.Executable
import org.junit.platform.commons.JUnitException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureTestDatabase
class IdentifierRegistryTest {

    @Autowired
    lateinit var identifierRegistry: IdentifierRegistry

    private val key: String = "2q4t09j0qj09fj́w09"


    @Test
    fun `revocation of non existing key should throw exception`() {
        Assertions.assertThrows(Exception::class.java, Executable {
            identifierRegistry.isRevoked(key)
        }, "Can not revoke what is not there (isnt registered)")
            .also { assertEquals(it.message, "Not registered", "Wrong Exception Text") }

        // alternatively
//        try {
//            identifierRegistery.revoke(key)
//        } catch (e: Exception) {
//            assertEquals(e.message, "Not registered", "Can not revoke what is not there (isnt registered)")
//            return
//        }
//        assertTrue(false, "Should have thrown an exception")
    }

    @Test
    fun `check on non existing key should throw an exception`() {
        Assertions.assertThrows(Exception::class.java, Executable {
                        identifierRegistry.isRevoked(key)
        }, "Can not check what is not there (isnt registered)")
            .also { assertEquals(it.message, "Not registered", "Wrong Exception Text") }

        // alternatively
//        try {
//            identifierRegistery.isRevoked(key)
//        } catch (e: Exception) {
//            assertEquals(e.message, "Not registered", "Can not revoke what is not there (isnt registered)")
//            return
//        }
//        Assertions.assertTrue(false, "Should have thrown an exception")
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `simple positive add and revoke key should work` () {
        identifierRegistry.addIdenitfier(key)
        identifierRegistry.isRevoked(key).also { assertEquals(false, it, "key is already revoked") }
        identifierRegistry.revoke(key)
        identifierRegistry.isRevoked(key).also { assertEquals(true, it, "Should be revoked but isnt") }
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `double adding key should throw exception` () {
        Assertions.assertThrows(Exception::class.java, Executable {
            identifierRegistry.addIdenitfier(key)
            identifierRegistry.addIdenitfier(key)
        }, "Double ID registration possible")
            .also { assertEquals(it.message, "Already registered", "Wrong Exception Text") }

        // alternatively
//        try {
//            identifierRegistery.addIdenitfier(key)
//            identifierRegistery.addIdenitfier(key)
//        } catch (e: Exception) {
//            assertEquals(e.message, "Already registered", "Double ID registration possible")
//            return
//        }
//        assertTrue(false, "Should have thrown an exception")
    }

}