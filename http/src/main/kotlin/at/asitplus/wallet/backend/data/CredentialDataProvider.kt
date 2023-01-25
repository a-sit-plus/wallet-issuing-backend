package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.data.CredentialSubject
import kotlinx.datetime.Instant

interface CredentialDataProvider {

    fun getClaim(subjectId: String, attributeName: String, bpk: String, maxExpiration: Instant)
            : KmmResult<CredentialToBeIssued>

    fun getCredential(subjectId: String, attributeType: String, bpk: String, maxExpiration: Instant)
            : KmmResult<CredentialToBeIssued>

    data class CredentialToBeIssued(
        val subject: CredentialSubject,
        val expiration: Instant,
        val attributeType: String,
        val attachments: List<CredentialToBeIssuedAttachment> = listOf(),
    ) {
        fun toLogString(): String {
            return "CredentialToBeIssued(subject=$subject, expiration=$expiration, attributeType='$attributeType', attachments=${attachments.map { it.toLogString() }})"
        }
    }

    data class CredentialToBeIssuedAttachment(
        val name: String,
        val mediaType: String,
        val data: ByteArray,
    ) {
        fun toIssuerCredentialDataProviderFormat() = Issuer.Attachment(name, mediaType, data)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CredentialToBeIssuedAttachment

            if (name != other.name) return false
            if (mediaType != other.mediaType) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + mediaType.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }

        fun toLogString(): String {
            return "CredentialToBeIssuedAttachment(name='$name', mediaType='$mediaType', data.size=${data.size})"
        }


    }
}