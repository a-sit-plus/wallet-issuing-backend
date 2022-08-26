package at.asitplus.wallet.backend.service

import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.NextMessage
import kotlinx.coroutines.runBlocking

/**
 * Provides a wrapper around [IssueCredentialMessenger] from the vclib,
 * essentially to wrap the suspending function [IssueCredentialMessenger.parseMessage]
 */
interface IssueCredentialAdapter {

    fun parseMessage(it: String): NextMessage?

}

class DefaultIssueCredentialAdapter(
    private val issueCredentialMessenger: IssueCredentialMessenger,
) : IssueCredentialAdapter {

    override fun parseMessage(it: String) =
        runBlocking { issueCredentialMessenger.parseMessage(it) }

}