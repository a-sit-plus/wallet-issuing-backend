package at.asitplus.wallet.backend.auth

interface NonceToBpkService {

    fun exchangeForBpk(nonce: String): String?

}

class SimpleNonceToBpkService : NonceToBpkService {

    override fun exchangeForBpk(nonce: String): String {
        return "bpk"
    }

}