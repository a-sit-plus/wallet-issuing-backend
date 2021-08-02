package at.asitplus.wallet.backend

import Utils.Companion.zlibCompress
import at.asitplus.wallet.backend.model.IdentifierRegistry
import at.asitplus.wallet.lib.agent.Agent
import at.asitplus.wallet.lib.toBase64
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.BitSet

@Service
class RevocationService {

    @Autowired
    lateinit var vcAgent: Agent

    @Autowired
    lateinit var identifierRegistry: IdentifierRegistry

    fun getRevocationCredential(): String {
        return vcAgent.issueCredential("TODO make own Credential with arguments: " + buildRevocationList())
    }

    /**
     * Returns a Base64-encoded, zlib-compressed bitstring of revoked credentials, where
     * the entry at "revocationListIndex" (of the credential) is true iff it is revoked
     */
    fun buildRevocationList(): String {
        val revocationList = identifierRegistry.getRevocationList()
        val bitset = BitSet(131072)
        revocationList.forEachIndexed { index, b -> bitset[index] = b }
        val zipped = bitset.toByteArray().zlibCompress()
        return zipped.toBase64()
    }


}