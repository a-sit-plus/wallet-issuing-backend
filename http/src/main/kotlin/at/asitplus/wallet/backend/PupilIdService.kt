package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.IssueCredentialMessenger
import at.asitplus.wallet.lib.agent.NextMessage
import kotlinx.coroutines.runBlocking

interface PupilIdService {

    fun parseMessage(it: String, bpk: String, deviceBindingCertificate: ByteArray): NextMessage?

}

class DefaultPupilIdService(
    private val issueCredentialMessengerPupilId: IssueCredentialMessenger,
) : PupilIdService {

    override fun parseMessage(it: String, bpk: String, deviceBindingCertificate: ByteArray) =
        runBlocking {
            return@runBlocking issueCredentialMessengerPupilId.parseMessage(it)
        }

}