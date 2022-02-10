package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.NextMessage

interface PupilIdService {

    suspend fun parseMessage(it: String): NextMessage

}

class DefaultPupilIdService(private val issueCredentialMessengerPupilId: IssueCredentialMessenger) : PupilIdService {

    override suspend fun parseMessage(it: String): NextMessage {
        return issueCredentialMessengerPupilId.parseMessage(it)
    }

}