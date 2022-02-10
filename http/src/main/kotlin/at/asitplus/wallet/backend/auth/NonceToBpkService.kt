package at.asitplus.wallet.backend.auth

interface NonceToBpkService {

    fun exchangeForBpk(nonce: String): String?

}

class DummyNonceToBpkService : NonceToBpkService {

    override fun exchangeForBpk(nonce: String): String {
        return "bpk"
    }

}