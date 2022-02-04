package at.asitplus.wallet.backend

import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class CertificateService {

    fun sign(csr: ByteArray): ByteArray {
        return Random.nextBytes(32)
    }

}