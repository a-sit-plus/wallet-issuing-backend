package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.NextMessage

interface MessengerAdapter {

    suspend fun startCreatingInvitation(): NextMessage

    suspend fun parseMessage(it: String): NextMessage

}

class IssueCredentialMessengerAdapter(val messenger: IssueCredentialMessenger) : MessengerAdapter {

    override suspend fun startCreatingInvitation(): NextMessage {
        return messenger.startCreatingInvitation()
    }

    override suspend fun parseMessage(it: String): NextMessage {
        return messenger.parseMessage(it)
    }
}
