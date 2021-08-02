package at.asitplus.wallet.backend

import Utils.Companion.zlibDecompress
import at.asitplus.wallet.backend.model.Identifier
import at.asitplus.wallet.backend.model.IdentifierRegistry
import at.asitplus.wallet.lib.fromBase64Url
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import java.util.BitSet
import java.util.UUID
import kotlin.test.assertContentEquals


@SpringBootTest
@AutoConfigureTestDatabase
class RevocationServiceTest {

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
        val toBeRevokedList = mutableListOf<Identifier>()
        for (i in 0..9) {
            val key = UUID.randomUUID().toString()
            val identifier = identifierRegistry.addIdentifier(key)
            if ((key.hashCode() % 2) == 0) {
                toBeRevokedList.add(identifier)
            }
        }
        val expectedRevocationList = BooleanArray(10) { false }
        for (toBeRevoked in toBeRevokedList) {
            identifierRegistry.revoke(toBeRevoked.key)
            expectedRevocationList[toBeRevoked.revocationListIndex.toInt()] = true
        }
        val revocationList = revocationService.buildRevocationList()
        val decompressed = revocationList.fromBase64Url().zlibDecompress()
        val result = BitSet.valueOf(decompressed)
        var indexInBitSet: Int = result.nextSetBit(0)
        while (indexInBitSet >= 0) {
            assertTrue(toBeRevokedList.find { it.revocationListIndex.toInt() == indexInBitSet } != null)
            if (indexInBitSet == Int.MAX_VALUE) {
                break // or (i+1) would overflow
            }
            indexInBitSet = result.nextSetBit(indexInBitSet + 1)
        }
    }

}