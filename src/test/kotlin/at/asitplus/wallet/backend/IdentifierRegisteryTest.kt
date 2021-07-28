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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
class IdentifierRegisteryTest {

    @Autowired
    lateinit var identifierRegistery: IdentifierRegistery

    @Test
    fun AddAndReoke() {
        val key: String = "2q4t09j0qj09fj́w09"
        identifierRegistery.addIdenitfier(key)
        identifierRegistery.isRevoked(key).also { Assertions.assertEquals(false, it, "key is already revoked") }
        identifierRegistery.revoke(key)
        identifierRegistery.isRevoked(key).also { Assertions.assertEquals(true, it, "Should be revoked but isnt") }
    }

    @Test
    fun DoubleAdd() {
        try {
        val key: String = "2q4t09j0qj09fj́w09"
        identifierRegistery.addIdenitfier(key)
        identifierRegistery.addIdenitfier(key)
        } catch (e: Exception) {
            Assertions.assertEquals(e.message, "Already registered", "Double ID registration possible")
        }
    }

}