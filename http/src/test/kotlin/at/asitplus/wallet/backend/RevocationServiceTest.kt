package at.asitplus.wallet.backend

import Utils.Companion.readBitString
import Utils.Companion.zlibDecompress
import at.asitplus.wallet.backend.model.IdentifierRegistry
import com.nimbusds.jose.util.Base64
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.test.assertContentEquals


@SpringBootTest
@AutoConfigureTestDatabase
class RevocationServiceTest {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    private lateinit var key: String

    @Autowired
    lateinit var identifierRegistry: IdentifierRegistry

    @Autowired
    lateinit var revocationService: RevocationService

    @BeforeEach
    fun beforeEach() {
        key = UUID.randomUUID().toString()
    }

    @Test
    fun `check revocation credential`() {
        val should =  BooleanArray(10) {false}
        for (i in 0..9) {
            val key = UUID.randomUUID().toString()
            identifierRegistry.addIdentifier(key)
            if ((key.hashCode() % 2) == 0) {
                identifierRegistry.revoke(key)
                should[i] = true
            }
        }
        val revocateionList = revocationService.buildRevocationList()
        val string = Base64.from(revocateionList)
        val decoded = string.decode()
        val unziped = decoded.zlibDecompress()
        val res = BooleanArray(unziped.size)
        readBitString(ByteArrayInputStream(unziped), res)
        assertContentEquals(should, res.copyOfRange(0, 10))
    }

}