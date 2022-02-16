package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.IssueCredentialProtocolResult
import at.asitplus.wallet.lib.agent.NextMessage
import kotlinx.coroutines.runBlocking

interface PupilIdService {

    fun parseMessage(it: String, bpk: String, deviceBindingCertificate: ByteArray): NextMessage?

}

class DefaultPupilIdService(
    private val issueCredentialMessengerPupilId: IssueCredentialMessenger,
) : PupilIdService {

    override fun parseMessage(it: String, bpk: String, deviceBindingCertificate: ByteArray): NextMessage? {
        return runBlocking {
            val parsedMessage = issueCredentialMessengerPupilId.parseMessage(it)
            if (parsedMessage is NextMessage.Result<*>) {
                val result = parsedMessage.result
                if (result is IssueCredentialProtocolResult) {
                    result.accepted.forEach {

                    }
                }
            }
            return@runBlocking parsedMessage
        }
    }

}