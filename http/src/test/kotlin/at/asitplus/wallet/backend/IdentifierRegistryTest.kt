package at.asitplus.wallet.backend

import Utils.Companion.readBitString
import Utils.Companion.writeBitString
import at.asitplus.wallet.backend.model.IdentifierRegistry
import at.asitplus.wallet.backend.model.IdentifierRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.test.assertContentEquals


@SpringBootTest
@AutoConfigureTestDatabase
class IdentifierRegistryTest {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    @Autowired
    lateinit var identifierRegistry: IdentifierRegistry

    @Autowired
    lateinit var identifierRepository: IdentifierRepository

    private lateinit var key: String

    @BeforeEach
    fun beforeEach() {
        key = UUID.randomUUID().toString()
        identifierRepository.deleteAll()
    }

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
    fun `simple positive add and revoke key should work`() {
        identifierRegistry.addIdentifier(key)
        identifierRegistry.isRevoked(key).also { assertEquals(false, it, "key is already revoked") }
        identifierRegistry.revoke(key)
        identifierRegistry.isRevoked(key).also { assertEquals(true, it, "Should be revoked but isn't") }
    }

    @Test
    fun `double adding key should throw exception`() {
        Assertions.assertThrows(Exception::class.java, {
            identifierRegistry.addIdentifier(key)
            identifierRegistry.addIdentifier(key)
        }, "Double ID registration possible")
            .also { assertEquals(it.message, "Already registered", "Wrong Exception Text") }
    }

    @Test
    fun `revocation list should match revocation calls`() {
        val should = BooleanArray(10) { false }
        for (i in 0..9) {
            val key = UUID.randomUUID().toString()
            identifierRegistry.addIdentifier(key)
            if ((key.hashCode() % 2) == 0) {
                identifierRegistry.revoke(key)
                should[i] = true
            }
        }
        val revocationList = identifierRegistry.getRevocationList()
        assertContentEquals(should, revocationList, "Revocation list should match revocation calls")
    }

    @Test
    fun `test encoding and decoding the binary list`() {
        val ar = booleanArrayOf(
            true,
            false,
            false,
            true,
            false,
            true,
            true,
            true,
            false,
            true,
            false,
            false,
            false,
            true,
            true
        )
        val out = ByteArrayOutputStream()
        writeBitString(out, ar)
        println("byte amount of size ${out.size()} bytes")
        assertEquals(2, out.size(), "Not a bit string")

        val res = BooleanArray(15)
        readBitString(ByteArrayInputStream(out.toByteArray()), res)
        assertContentEquals(ar, res, "not the same booleans after decoding")
        out.close()

        val out1 = ByteArrayOutputStream()
        writeBitString(out1, BooleanArray(17) { (it % 2) != 0 })
        println("byte amount of size ${out1.size()} bytes")
        assertEquals(3, out1.size(), "Not a bit string")
    }

}