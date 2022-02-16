package at.asitplus.wallet.backend.auth

interface NonceToBpkService {

    fun validate(nonce: String): String?

}

class DummyNonceToBpkService : NonceToBpkService {

    override fun validate(nonce: String): String {
        return "bpk"
    }

}