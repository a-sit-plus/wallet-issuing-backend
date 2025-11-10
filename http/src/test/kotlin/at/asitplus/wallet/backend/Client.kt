package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.oauth2.OAuth2Client
import at.asitplus.wallet.lib.oidvci.WalletService

class Client {

    val randomKeyAdapter = EphemeralKeyWithSelfSignedCert()

    val oid4vciClient = WalletService(keyMaterial = randomKeyAdapter)

    val oauth2Client = OAuth2Client()

}