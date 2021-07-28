package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.data.Agent
import at.asitplus.wallet.backend.data.VerifiableCredentialSerialized
import at.asitplus.wallet.backend.model.IdentifierRegistery
import org.assertj.core.api.JUnitSoftAssertions
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
class IdentifierRegisteryTest {

    @Autowired
    lateinit var identifierRegistery: IdentifierRegistery

    private val key: String = "2q4t09j0qj09fj́w09"


    @Test
    fun OnlyRevoke() {
        try {
            identifierRegistery.revoke(key)
        } catch (e: Exception) {
            Assertions.assertEquals(e.message, "Not registered", "Can not revoke what is not there (isnt registered)")
            return
        }
        Assertions.assertTrue(false, "Should have thrown an exception")
    }

    @Test
    fun OnlyCheckRevocation() {
        try {
            identifierRegistery.isRevoked(key)
        } catch (e: Exception) {
            Assertions.assertEquals(e.message, "Not registered", "Can not revoke what is not there (isnt registered)")
            return
        }
        Assertions.assertTrue(false, "Should have thrown an exception")
    }

    @Test
    @DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
    fun AddAndRevoke() {
        identifierRegistery.addIdenitfier(key)
        identifierRegistery.isRevoked(key).also { Assertions.assertEquals(false, it, "key is already revoked") }
        identifierRegistery.revoke(key)
        identifierRegistery.isRevoked(key).also { Assertions.assertEquals(true, it, "Should be revoked but isnt") }
    }

    @Test
    @DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
    fun DoubleAdd() {
        try {
            identifierRegistery.addIdenitfier(key)
            identifierRegistery.addIdenitfier(key)
        } catch (e: Exception) {
            Assertions.assertEquals(e.message, "Already registered", "Double ID registration possible")
            return
        }
        Assertions.assertTrue(false, "Should have thrown an exception")
    }

}