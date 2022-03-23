package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.NextMessage
import kotlinx.coroutines.runBlocking

interface IssueCredentialAdapter {

    fun parseMessage(it: String): NextMessage?

}

class DefaultIssueCredentialAdapter(
    private val issueCredentialMessenger: IssueCredentialMessenger,
) : IssueCredentialAdapter {

    override fun parseMessage(it: String) =
        runBlocking {
            return@runBlocking issueCredentialMessenger.parseMessage(it)
        }

}