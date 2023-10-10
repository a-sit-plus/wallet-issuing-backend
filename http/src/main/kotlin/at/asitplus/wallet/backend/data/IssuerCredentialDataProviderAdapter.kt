package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.wallet.backend.data.CredentialDataProvider.CredentialToBeIssuedAttachment
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.cbor.CoseKey
import kotlinx.datetime.Clock
import kotlin.time.Duration


/**
 * Implements interface from VC Library to wrap calls to a specific [CredentialDataProvider].
 */
class IssuerCredentialDataProviderAdapter(
    private val lifetime: Duration,
    private val credentialDataProvider: CredentialDataProvider,
) : IssuerCredentialDataProvider {

    override fun getCredentialWithType(
        subjectId: String,
        subjectPublicKey: CoseKey?,
        attributeTypes: Collection<String>
    ): KmmResult<List<CredentialToBeIssued>> {
        val cappedExpiration = Clock.System.now() + lifetime
        val credential = credentialDataProvider.getCredentialWithType(
            subjectId,
            attributeTypes,
            cappedExpiration,
            subjectPublicKey,
        )

        return credential.map { list ->
            list.map {
                when (it) {
                    is CredentialDataProvider.CredentialToBeIssued.Iso -> CredentialToBeIssued.Iso(
                        it.issuerSignedItems, it.subjectPublicKey, it.expiration, it.attributeType
                    )

                    is CredentialDataProvider.CredentialToBeIssued.Vc -> CredentialToBeIssued.Vc(
                        it.subject,
                        it.expiration,
                        it.attributeType,
                        it.attachments.map(CredentialToBeIssuedAttachment::toIssuerCredentialDataProviderFormat),
                    )

                }
            }
        }
    }

}
