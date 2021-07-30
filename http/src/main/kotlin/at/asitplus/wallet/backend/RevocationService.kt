package at.asitplus.wallet.backend

import Utils.Companion.writeBitString
import Utils.Companion.zlibCompress
import at.asitplus.wallet.backend.model.IdentifierRegistry
import at.asitplus.wallet.lib.agent.Agent
import com.nimbusds.jose.util.Base64
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

@Autowired
lateinit var vcAgent: Agent

@Autowired
lateinit var identifierRegistry: IdentifierRegistry

@Service
class RevocationService {
    fun getRevocationCredential(): String {

        return vcAgent.issueCredential("TODO make own Credential with arguments: " + buildRevocationList())
    }

    fun buildRevocationList(): String {
        val revocationList = identifierRegistry.getRevocationList()
        val out = ByteArrayOutputStream()
        writeBitString(out, revocationList)
        val byteList = out.toByteArray()
        val bitstring = ByteArray(16*1000) {0}
        byteList.copyInto(bitstring, 0, byteList.size)
        // zlib compressed
        val zipped = byteList.zlibCompress()
        // base64 encoded
        return Base64.encode(zipped).toJSONString()
    }



}