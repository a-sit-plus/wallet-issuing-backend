package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.pki.RandomKeyAdapter
import at.asitplus.wallet.backend.service.DefaultCryptoServiceAdapter
import at.asitplus.wallet.lib.cbor.DefaultCoseService
import at.asitplus.wallet.lib.jws.DefaultJwsService
import at.asitplus.wallet.lib.oidvci.WalletService

class Client {

    val randomKeyAdapter = RandomKeyAdapter()
    val cryptoService = DefaultCryptoServiceAdapter(randomKeyAdapter)
    val jsonWebKey = randomKeyAdapter.jsonWebKey

    val oid4vciClient = WalletService(
        cryptoService = cryptoService,
        jwsService = DefaultJwsService(cryptoService),
        coseService = DefaultCoseService(cryptoService),
    )

    constructor(lifetimeSeconds: Long = 60) {

    }


}