package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.pki.RandomKeyAdapter
import at.asitplus.wallet.lib.agent.DefaultCryptoService
import at.asitplus.wallet.lib.agent.JvmKeyPairAdapter
import at.asitplus.wallet.lib.cbor.DefaultCoseService
import at.asitplus.wallet.lib.jws.DefaultJwsService
import at.asitplus.wallet.lib.oidvci.WalletService

class Client {

    val randomKeyAdapter = RandomKeyAdapter().run { JvmKeyPairAdapter(keyPair, signingAlgorithm, certificate) }
    val cryptoService = DefaultCryptoService(randomKeyAdapter)

    val oid4vciClient = WalletService(
        cryptoService = cryptoService,
        jwsService = DefaultJwsService(cryptoService),
        coseService = DefaultCoseService(cryptoService),
    )


}