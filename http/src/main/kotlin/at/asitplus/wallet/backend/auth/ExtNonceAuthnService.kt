package at.asitplus.wallet.backend.auth

interface ExtNonceAuthnService {

    fun validate(nonce: String): String?

}

class DummyExtNonceAuthnService : ExtNonceAuthnService {

    override fun validate(nonce: String): String {
        return "bpk"
    }

}